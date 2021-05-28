package actions

import controllers.ErrorHandler
import models._
import play.api.i18n.MessagesApi
import play.api.mvc.{ ActionRefiner, Result }
import repositories.RzMetaGitRepository

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class RepositoryAction @Inject() (
  messagesApi: MessagesApi,
  gitEntitiesRepository: RzMetaGitRepository,
  errorHandler: ErrorHandler
)(implicit val ec: ExecutionContext) {
  def on(
    ownerName: String,
    repoName: String,
    minRole: Role
  ): ActionRefiner[AccountRequest, RepositoryRequest] =
    new ActionRefiner[AccountRequest, RepositoryRequest] {
      def executionContext: ExecutionContext = ec

      private def checkAccess(account: Account): Future[Either[RzError, (RzRepository, Role)]] =
        gitEntitiesRepository.getByOwnerAndName(ownerName, repoName).flatMap {
          case Right(repo: RzRepository) =>
            gitEntitiesRepository.getCollaborator(account, repo).map {
              case Right(c: Collaborator) if c.role.weight >= minRole.weight => Right((repo, c.role))

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
