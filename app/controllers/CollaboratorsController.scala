package controllers

import actions.{ AuthenticatedAction, RepositoryAction }
import models._
import play.api.data.Form
import play.api.data.FormBinding.Implicits.formBinding
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.Results._
import play.api.mvc.{ Action, AnyContent, Result }
import repositories.{ AccountRepository, RzMetaGitRepository }
import views.html

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class CollaboratorsController @Inject() (
  metaGitRepository: RzMetaGitRepository,
  accountRepository: AccountRepository,
  authAction: AuthenticatedAction,
  repoAction: RepositoryAction
)(implicit ec: ExecutionContext) {
  val addCollaboratorForm: Form[NewCollaboratorData] = Form(
    mapping("emailOrLogin" -> nonEmptyText, "role" -> nonEmptyText)(NewCollaboratorData.apply)(
      NewCollaboratorData.unapply
    )
  )

  val removeCollaboratorForm: Form[RemoveCollaboratorData] = Form(
    mapping("email" -> nonEmptyText)(RemoveCollaboratorData.apply)(RemoveCollaboratorData.unapply)
  )

  def collaboratorsPage(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Owner)).async { implicit req =>
      metaGitRepository.getCollaborators(req.repository).map { list =>
        Ok(html.repository.collaborators(addCollaboratorForm, list))
      }
    }

  private def pageRedirect(req: RepositoryRequest[AnyContent]): Result =
    Redirect(routes.CollaboratorsController.collaboratorsPage(req.repository.owner.userName, req.repository.name))

  private def pageRedirectWithError(messageId: String)(implicit req: RepositoryRequest[AnyContent]): Future[Result] =
    Future(
      pageRedirect(req)
        .flashing("error" -> Messages(messageId))
    )

  private def badRequest(form: Form[NewCollaboratorData])(implicit req: RepositoryRequest[AnyContent]) =
    metaGitRepository
      .getCollaborators(req.repository)
      .map(list => BadRequest(html.repository.collaborators(form, list)))

  private def add(account: Account, role: String, repo: RzRepository): Future[_] = {
    val rzRole       = Role.fromString(role).getOrElse(Role.Viewer)
    val collaborator = new Collaborator(account, rzRole)
    metaGitRepository.addCollaborator(collaborator, repo)
  }

  def addCollaborator(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Owner)).async {
      implicit req: RepositoryRequest[AnyContent] =>
        addCollaboratorForm
          .bindFromRequest()
          .fold(
            formWithErrors => badRequest(formWithErrors)(req),
            data =>
              accountRepository.getByUsernameOrEmail(data.emailOrLogin).flatMap {
                case Right(account: Account) =>
                  metaGitRepository.isAccountCollaborator(account, req.repository).flatMap {
                    case true => pageRedirectWithError("repository.collaborator.error.alreadycollab")
                    case false =>
                      add(account, data.role, req.repository).map(_ => pageRedirect(req))
                  }
                case _ => pageRedirectWithError("repository.collaborator.error.nosuchuser")
              }
          )
    }

  def removeCollaborator(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Owner)).async { implicit req =>
      removeCollaboratorForm
        .bindFromRequest()
        .fold(
          _ => badRequest(addCollaboratorForm)(req),
          data =>
            accountRepository.getByUsernameOrEmail(data.id).flatMap {
              case Right(account: Account) =>
                metaGitRepository.removeCollaborator(account, req.repository).map(_ => pageRedirect(req))
              case _ => pageRedirectWithError("repository.collaborator.error.nosuchuser")
            }
        )
    }

}
