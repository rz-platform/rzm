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
        list <- collaboratorRepository.getCollaborators(req.doc)

        f = list.filter(c => c.role != Role.Owner)
      } yield Ok(html.document.collaboratorslist(addCollaboratorForm, f))
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
                  collaboratorRepository.isAccountCollaborator(account, req.doc).flatMap {
                    case true => pageRedirectWithError("doc.collaborator.error.alreadycollab")
                    case false =>
                      for {
                        _ <- collaboratorRepository.addCollaborator(account, data.role, req.doc)
                      } yield pageRedirect(req)
                  }
                case _ => pageRedirectWithError("doc.collaborator.error.nosuchuser")
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
                  _ <- collaboratorRepository.removeCollaborator(account, req.doc)
                } yield pageRedirect(req)
              case _ => pageRedirectWithError("doc.collaborator.error.nosuchuser")
            }
        )
    }

  private def pageRedirect(req: RepositoryRequest[AnyContent]): Result =
    Redirect(routes.CollaboratorsController.page(req.doc.owner.username, req.doc.name))

  private def pageRedirectWithError(messageId: String)(implicit req: RepositoryRequest[AnyContent]): Future[Result] =
    Future(
      pageRedirect(req)
        .flashing("error" -> Messages(messageId))
    )

  private def badRequest(
    form: Form[NewCollaboratorDetails]
  )(implicit req: RepositoryRequest[AnyContent]): Future[Result] =
    for {
      list <- collaboratorRepository.getCollaborators(req.doc)
    } yield BadRequest(html.document.collaboratorslist(form, list))
}
