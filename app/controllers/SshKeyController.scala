package controllers

import actions.AuthenticatedAction
import models.{ RzRegex, SshKey, SshKeyData, SshRemoveData }
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms.{ mapping, nonEmptyText }
import play.api.data.validation.Constraints.pattern
import play.api.i18n.Messages
import play.api.mvc.{ Action, AnyContent, MessagesAbstractController, MessagesControllerComponents }
import repositories.AccountRepository
import views.html

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class SshKeyController @Inject() (
  accountService: AccountRepository,
  authAction: AuthenticatedAction,
  config: Configuration,
  cc: MessagesControllerComponents
)(
  implicit ec: ExecutionContext
) extends MessagesAbstractController(cc) {
  private val maximumNumberPerUser: Int = config.get[String]("play.server.ssh.maximumNumberPerUser").toInt

  val addSshKeyForm: Form[SshKeyData] = Form(
    mapping(
      "publicKey" -> nonEmptyText.verifying(
        pattern(RzRegex.publicKey)
      )
    )(SshKeyData.apply)(SshKeyData.unapply)
  )
  val deleteSshKeyForm: Form[SshRemoveData] = Form(
    mapping(
      "id" -> nonEmptyText
    )(SshRemoveData.apply)(SshRemoveData.unapply)
  )

  def keysPage(): Action[AnyContent] = authAction.async { implicit req =>
    accountService.listSshKeys(req.account).map(list => Ok(html.sshKeys(list, addSshKeyForm, deleteSshKeyForm)))
  }

  def addSshKey(): Action[AnyContent] = authAction.async { implicit request =>
    addSshKeyForm.bindFromRequest().fold(
      formWithErrors =>
        accountService.listSshKeys(request.account).map { list =>
          Ok(html.sshKeys(list, formWithErrors, deleteSshKeyForm))
        },
      sshKeyData =>
        accountService.cardinalitySshKey(request.account).flatMap {
          case Right(c: Long) if c < maximumNumberPerUser =>
            val key = new SshKey(sshKeyData.publicKey, request.account)
            accountService
              .setSshKey(request.account, key)
              .map(_ =>
                Redirect(routes.SshKeyController.keysPage())
                  .flashing("success" -> Messages("profile.ssh.notification.added"))
              )
          case _ =>
            Future(
              Redirect(routes.SshKeyController.keysPage())
                .flashing("error" -> Messages("profile.ssh.notification.toomuch"))
            )
        }
    )
  }

  def deleteSshKey(): Action[AnyContent] = authAction.async { implicit request =>
    deleteSshKeyForm.bindFromRequest().fold(
      formWithErrors =>
        accountService.listSshKeys(request.account).map { list =>
          Ok(html.sshKeys(list, addSshKeyForm, formWithErrors))
        },
      (sshKeyIdData: SshRemoveData) =>
        accountService
          .deleteSshKey(request.account, sshKeyIdData.id)
          .map(_ =>
            Redirect(routes.SshKeyController.keysPage())
              .flashing("success" -> Messages("profile.ssh.notification.removed"))
          ) // TODO: check ownership
    )
  }

}
