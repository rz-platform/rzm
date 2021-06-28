package documents.controllers

import collaborators.models.{ Collaborator, Role }
import collaborators.repositories.CollaboratorRepository
import documents.models.{ RepositoryRequest, RzRepository }
import documents.repositories.RzMetaGitRepository
import infrastructure.controllers.ErrorHandler
import infrastructure.errors.{ AccessDenied, NotFoundInRepository, RepositoryError }
import play.api.i18n.MessagesApi
import play.api.mvc.{ ActionRefiner, Result }
import users.models.{ Account, AccountRequest }

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class RepositoryAction @Inject() (
  messagesApi: MessagesApi,
  gitEntitiesRepository: RzMetaGitRepository,
  collaboratorRepository: CollaboratorRepository,
  errorHandler: ErrorHandler
)(implicit val ec: ExecutionContext) {
  def on(
    ownerName: String,
    repoName: String,
    minRole: Role
  ): ActionRefiner[AccountRequest, RepositoryRequest] =
    new ActionRefiner[AccountRequest, RepositoryRequest] {
      def executionContext: ExecutionContext = ec

      private def checkAccess(account: Account): Future[Either[RepositoryError, (RzRepository, Role)]] =
        gitEntitiesRepository.getByOwnerAndName(ownerName, repoName).flatMap {
          case Right(repo: RzRepository) =>
            collaboratorRepository.getCollaborator(account.id, repo).map {
              case Some(c: Collaborator) if c.role.perm >= minRole.perm => Right((repo, c.role))

              case _ => Left(AccessDenied)
            }
          case _ => Future(Left(NotFoundInRepository))
        }

      def refine[A](
        request: AccountRequest[A]
      ): Future[Either[Result, RepositoryRequest[A]]] =
        checkAccess(request.account).flatMap {
          case Right(data) =>
            val (repository: RzRepository, access: Role) = data
            Future(Right(new RepositoryRequest[A](request, repository, request.account, access, messagesApi)))
          case Left(AccessDenied) =>
            Future(Left(errorHandler.clientError(request, msg = request.messages("error.accessdenied"))))
          case Left(_) =>
            Future(Left(errorHandler.clientError(request, msg = request.messages("error.notfound"))))
        }
    }
}
