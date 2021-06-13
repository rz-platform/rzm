package controllers

import actions.AuthenticatedAction
import forms.SshForms._
import models.SshKey
import play.api.Configuration
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
  cc: MessagesControllerComponents,
  errorHandler: ErrorHandler
)(
  implicit ec: ExecutionContext
) extends MessagesAbstractController(cc) {
  private val maximumNumberPerUser: Int = config.get[String]("play.server.ssh.maximumNumberPerUser").toInt

  def page(): Action[AnyContent] = authAction.async { implicit req =>
    accountService.listSshKeys(req.account).map(list => Ok(html.sshKeys(list, addSshKeyForm, deleteSshKeyForm)))
  }

  def addKey(): Action[AnyContent] = authAction.async { implicit request =>
    addSshKeyForm
      .bindFromRequest()
      .fold(
        formWithErrors =>
          accountService.listSshKeys(request.account).map { list =>
            Ok(html.sshKeys(list, formWithErrors, deleteSshKeyForm))
          },
        sshKeyData =>
          accountService.cardinalitySshKey(request.account).flatMap {
            case Right(c: Long) if c < maximumNumberPerUser =>
              val key = new SshKey(sshKeyData.publicKey, request.account)
              for {
                _ <- accountService.setSshKey(request.account, key)
              } yield Redirect(routes.SshKeyController.page())
                .flashing("success" -> Messages("profile.ssh.notification.added"))
            case _ =>
              Future(
                Redirect(routes.SshKeyController.page())
                  .flashing("error" -> Messages("profile.ssh.notification.toomuch"))
              )
          }
      )
  }

  def deleteKey(): Action[AnyContent] = authAction.async { implicit request =>
    deleteSshKeyForm
      .bindFromRequest()
      .fold(
        formWithErrors =>
          accountService.listSshKeys(request.account).map { list =>
            Ok(html.sshKeys(list, addSshKeyForm, formWithErrors))
          },
        data =>
          accountService.getSshKey(data.id).flatMap {
            case Some(key) if key.owner.id == request.account.id =>
              for {
                _ <- accountService.deleteSshKey(request.account, data.id)
              } yield Redirect(routes.SshKeyController.page())
                .flashing("success" -> Messages("profile.ssh.notification.removed"))
            case _ => Future(errorHandler.clientError(request, msg = request.messages("error.accessdenied")))
          }
      )
  }

}
