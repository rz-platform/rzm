package controllers

import actions.AuthenticatedAction
import forms.AccountForms._
import forms.FormErrors
import infrastructure.RzDateTime
import models._
import play.api.data._
import play.api.i18n.Messages
import play.api.mvc._
import repositories._
import views._

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class AccountController @Inject() (
  accountRepository: AccountRepository,
  authAction: AuthenticatedAction,
  errorHandler: ErrorHandler,
  cc: MessagesControllerComponents
)(
  implicit ec: ExecutionContext
) extends MessagesAbstractController(cc) {

  private val logger = play.api.Logger(this.getClass)

  private val zoneIds = RzDateTime.zoneIds

  def signup: Action[AnyContent] = Action.async(implicit request => Future(Ok(html.signup(signupForm, zoneIds))))

  def saveAccount: Action[AnyContent] = Action.async { implicit request =>
    val incomingData = request.body.asFormUrlEncoded
    val cleanData    = clear(incomingData)
    signupForm
      .bindFromRequest(cleanData)
      .fold(
        formWithErrors => Future(BadRequest(html.signup(formWithErrors, zoneIds))),
        data =>
          accountRepository.getByUsernameOrEmail(data.username).flatMap { // TODO accountData.email
            case Left(NotFoundInRepository) =>
              val account  = new Account(data)
              val username = AccountUsername.asEntity(account)
              val email    = AccountEmail.asEntity(account)
              val password = AccountPassword.asEntity(account, HashedString.fromString(data.password).toString)
              for {
                _      <- accountRepository.set(account, username, email, password)
                result <- authAction.authorize(account, request.session)
              } yield result
            case _ =>
              val newForm = FormErrors.error[AccountRegistrationData](
                signupForm.bindFromRequest(),
                FormError("username", Messages("signup.error.alreadyexists"))
              )
              Future(BadRequest(html.signup(newForm, zoneIds)))
          }
      )
  }

  def accountPage: Action[AnyContent] = authAction.async { implicit request =>
    accountRepository.getById(request.account.key).flatMap {
      case Right(account) => Future(Ok(html.profile(filledAccountEditForm(account), updatePasswordForm, zoneIds)))
      case _              => Future(errorHandler.clientError(request, msg = request.messages("error.notfound")))
    }
  }

  private def isEmailAvailable(currentEmail: String, newEmail: String): Future[Boolean] =
    if (currentEmail != newEmail) {
      accountRepository.getById(newEmail).flatMap { // TODO
        case Right(_) => Future(false)
        case _        => Future(true)
      }
    } else Future(true)

  def editAccount: Action[AnyContent] = authAction.async { implicit req =>
    accountEditForm
      .bindFromRequest()
      .fold(
        formWithErrors => Future(BadRequest(html.profile(formWithErrors, updatePasswordForm, zoneIds))),
        accountData =>
          isEmailAvailable(req.account.email, accountData.email).flatMap {
            case true =>
              for {
                _ <- accountRepository.update(req.account, req.account.fromForm(req.account.id, accountData))
              } yield Ok(html.profile(accountEditForm.bindFromRequest(), updatePasswordForm, zoneIds))
            case false =>
              val newForm = FormErrors.error[AccountData](
                accountEditForm.bindFromRequest(),
                FormError("mailAddress", Messages("profile.error.emailalreadyexists"))
              )
              Future(BadRequest(html.profile(newForm, updatePasswordForm, zoneIds)))
          }
      )
  }

  def setTimeZone(): Action[AnyContent] = authAction.async { implicit req =>
    timeZoneForm
      .bindFromRequest()
      .fold(
        _ => Future(Redirect(routes.AccountController.accountPage())),
        data =>
          for {
            _ <- accountRepository.setTimezone(req.account, data.tz)
          } yield Redirect(routes.AccountController.accountPage())
            .flashing("success" -> Messages("profile.flash.tzupdated"))
      )
  }

  def updatePassword(): Action[AnyContent] = authAction.async { implicit req =>
    updatePasswordForm
      .bindFromRequest()
      .fold(
        formWithErrors => Future(BadRequest(html.profile(filledAccountEditForm(req.account), formWithErrors, zoneIds))),
        passwordData =>
          accountRepository.getPassword(req.account).flatMap {
            case Right(passwordHash: String) if HashedString(passwordHash).check(passwordData.newPassword) =>
              val newPasswordHash = HashedString.fromString(passwordData.newPassword).toString
              val password        = AccountPassword.asEntity(req.account, newPasswordHash)
              for {
                _ <- accountRepository.setPassword(password)
              } yield Redirect(routes.AccountController.accountPage())
                .flashing("success" -> Messages("profile.flash.passupdated"))
            case _ =>
              val newForm = FormErrors.error[PasswordData](
                updatePasswordForm.bindFromRequest(),
                FormError("oldPassword", Messages("profile.error.passisincorrect"))
              )
              Future(BadRequest(html.profile(filledAccountEditForm(req.account), newForm, zoneIds)))
          }
      )
  }
}
