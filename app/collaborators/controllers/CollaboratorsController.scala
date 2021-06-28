package collaborators.controllers

import collaborators.models.{ NewCollaboratorDetails, Role }
import collaborators.repositories.CollaboratorRepository
import collaborators.validations.CollaboratorForms.{ addCollaboratorForm, removeCollaboratorForm }
import documents.controllers.RepositoryAction
import documents.models.RepositoryRequest
import documents.repositories.RzMetaGitRepository
import play.api.data.Form
import play.api.data.FormBinding.Implicits.formBinding
import play.api.i18n.Messages
import play.api.mvc.Results._
import play.api.mvc.{ Action, AnyContent, Result }
import users.controllers.AuthenticatedAction
import users.models.Account
import users.repositories.AccountRepository
import views.html

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class CollaboratorsController @Inject() (
  metaGitRepository: RzMetaGitRepository,
  collaboratorRepository: CollaboratorRepository,
  accountRepository: AccountRepository,
  authAction: AuthenticatedAction,
  repoAction: RepositoryAction
)(implicit ec: ExecutionContext) {
  def page(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Owner)).async { implicit req =>
      for {
        list <- collaboratorRepository.getCollaborators(req.repository)

        f = list.filter(c => c.role != Role.Owner)
      } yield Ok(html.repository.collaboratorslist(addCollaboratorForm, f))
    }

  def add(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Owner)).async {
      implicit req: RepositoryRequest[AnyContent] =>
        addCollaboratorForm
          .bindFromRequest()
          .fold(
            formWithErrors => badRequest(formWithErrors)(req),
            data =>
              accountRepository.getByUsernameOrEmail(data.emailOrLogin).flatMap {
                case Right(account: Account) =>
                  collaboratorRepository.isAccountCollaborator(account, req.repository).flatMap {
                    case true => pageRedirectWithError("repository.collaborator.error.alreadycollab")
                    case false =>
                      for {
                        _ <- collaboratorRepository.addCollaborator(account, data.role, req.repository)
                      } yield pageRedirect(req)
                  }
                case _ => pageRedirectWithError("repository.collaborator.error.nosuchuser")
              }
          )
    }

  def remove(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Owner)).async { implicit req =>
      removeCollaboratorForm
        .bindFromRequest()
        .fold(
          _ => badRequest(addCollaboratorForm)(req),
          data =>
            accountRepository.getByUsernameOrEmail(data.id).flatMap {
              case Right(account: Account) =>
                for {
                  _ <- collaboratorRepository.removeCollaborator(account, req.repository)
                } yield pageRedirect(req)
              case _ => pageRedirectWithError("repository.collaborator.error.nosuchuser")
            }
        )
    }

  private def pageRedirect(req: RepositoryRequest[AnyContent]): Result =
    Redirect(routes.CollaboratorsController.page(req.repository.owner.username, req.repository.name))

  private def pageRedirectWithError(messageId: String)(implicit req: RepositoryRequest[AnyContent]): Future[Result] =
    Future(
      pageRedirect(req)
        .flashing("error" -> Messages(messageId))
    )

  private def badRequest(
    form: Form[NewCollaboratorDetails]
  )(implicit req: RepositoryRequest[AnyContent]): Future[Result] =
    for {
      list <- collaboratorRepository.getCollaborators(req.repository)
    } yield BadRequest(html.repository.collaboratorslist(form, list))
}
