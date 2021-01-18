package controllers

import actions.{ AuthenticatedAction, RepositoryAction }
import models._
import play.api.data.Form
import play.api.data.Forms.{ mapping, _ }
import play.api.i18n.Messages
import play.api.mvc.Results._
import play.api.mvc.{ Action, AnyContent, Result }
import repositories.{ AccountRepository, RzGitRepository }
import views.html

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class CollaboratorsController @Inject() (
  gitEntitiesRepository: RzGitRepository,
  repositoryAction: RepositoryAction,
  authenticatedAction: AuthenticatedAction,
  accountRepository: AccountRepository
)(implicit ec: ExecutionContext) {
  val addCollaboratorForm: Form[NewCollaboratorData] = Form(
    mapping("emailOrLogin" -> nonEmptyText, "accessLevel" -> nonEmptyText)(NewCollaboratorData.apply)(
      NewCollaboratorData.unapply
    )
  )

  val removeCollaboratorForm: Form[RemoveCollaboratorData] = Form(
    mapping("email" -> nonEmptyText)(RemoveCollaboratorData.apply)(RemoveCollaboratorData.unapply)
  )

  def collaboratorsPage(accountName: String, repositoryName: String): Action[AnyContent] =
    authenticatedAction.andThen(repositoryAction.on(accountName, repositoryName, OwnerAccess)).async { implicit req =>
      gitEntitiesRepository.getCollaborators(req.repository).map { list =>
        Ok(html.git.collaborators(addCollaboratorForm, list))
      }
    }

  private def collaboratorPageRedirect(req: RepositoryRequest[AnyContent]): Result =
    Redirect(routes.CollaboratorsController.collaboratorsPage(req.repository.owner.userName, req.repository.name))

  def addCollaborator(accountName: String, repositoryName: String): Action[AnyContent] =
    authenticatedAction.andThen(repositoryAction.on(accountName, repositoryName, OwnerAccess)).async {
      implicit req: RepositoryRequest[AnyContent] =>
        addCollaboratorForm.bindFromRequest.fold(
          formWithErrors =>
            gitEntitiesRepository.getCollaborators(req.repository).map { list =>
              BadRequest(html.git.collaborators(formWithErrors, list))
            },
          (data: NewCollaboratorData) =>
            accountRepository.getByUsernameOrEmail(data.emailOrLogin).flatMap {
              case Right(account: Account) =>
                gitEntitiesRepository.isAccountCollaborator(account, req.repository).flatMap {
                  case true =>
                    Future(
                      collaboratorPageRedirect(req)
                        .flashing("error" -> Messages("repository.collaborator.error.alreadycollab"))
                    )
                  case false =>
                    val c =
                      new Collaborator(account, AccessLevel.fromString(data.accessLevel).getOrElse(ViewAccess))
                    gitEntitiesRepository.addCollaborator(c, req.repository).map(_ => collaboratorPageRedirect(req))
                }
              case _ =>
                Future(
                  collaboratorPageRedirect(req)
                    .flashing("error" -> Messages("repository.collaborator.error.nosuchuser"))
                )
            }
        )
    }

  def removeCollaborator(accountName: String, repositoryName: String): Action[AnyContent] =
    authenticatedAction.andThen(repositoryAction.on(accountName, repositoryName, OwnerAccess)).async { implicit req =>
      removeCollaboratorForm.bindFromRequest.fold(
        _ =>
          gitEntitiesRepository.getCollaborators(req.repository).map { list =>
            BadRequest(
              html.git.collaborators(addCollaboratorForm, list)
            )
          },
        (data: RemoveCollaboratorData) =>
          accountRepository.getByUsernameOrEmail(data.id).flatMap {
            case Right(account: Account) =>
              gitEntitiesRepository.removeCollaborator(account, req.repository).map(_ => collaboratorPageRedirect(req))
            case _ =>
              Future(
                collaboratorPageRedirect(req).flashing("error" -> Messages("repository.collaborator.error.nosuchuser"))
              )
          }
      )
    }

}
