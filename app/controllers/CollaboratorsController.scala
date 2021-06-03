package controllers

import actions.{ AuthenticatedAction, RepositoryAction }
import forms.CollaboratorForms.{ addCollaboratorForm, removeCollaboratorForm }
import models._
import play.api.data.Form
import play.api.data.FormBinding.Implicits.formBinding
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
  def page(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Owner)).async { implicit req =>
      metaGitRepository.getCollaborators(req.repository).map { list =>
        Ok(html.repository.collaborators(addCollaboratorForm, list))
      }
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
                  metaGitRepository.isAccountCollaborator(account, req.repository).flatMap {
                    case true => pageRedirectWithError("repository.collaborator.error.alreadycollab")
                    case false =>
                      for {
                        _ <- metaGitRepository.addCollaborator(collaborator(account, data.role), req.repository)
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
                  _ <- metaGitRepository.removeCollaborator(account, req.repository)
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

  private def badRequest(form: Form[NewCollaboratorData])(implicit req: RepositoryRequest[AnyContent]): Future[Result] =
    for {
      list <- metaGitRepository.getCollaborators(req.repository)
    } yield BadRequest(html.repository.collaborators(form, list))

  private def collaborator(account: Account, role: String) = {
    val rzRole = Role.fromString(role).getOrElse(Role.Viewer)
    new Collaborator(account, rzRole)
  }
}
