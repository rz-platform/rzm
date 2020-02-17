package controllers

import scala.util.{Failure, Success, Try}
import javax.inject.Inject
import models.{Account, AccountData, AccountLoginData, AccountRegistrationData, AccountRepository, PasswordData}
import services.encryption._
import play.api.data.Forms._
import play.api.data._
import play.api.mvc._
import views._

import scala.concurrent.{ExecutionContext, Future}

class AuthController @Inject() (
  accountService: AccountRepository,
  userAction: UserInfoAction,
  cc: MessagesControllerComponents
)(
  implicit ec: ExecutionContext
) extends MessagesAbstractController(cc) {

  val loginForm = Form(
    mapping("userName" -> nonEmptyText, "password" -> nonEmptyText)(AccountLoginData.apply)(AccountLoginData.unapply)
  )

  val registerForm = Form(
    mapping(
      "userName" -> nonEmptyText,
      "fullName" -> optional(text),
      "password" -> nonEmptyText,
      "mailAddress" -> email
    )(AccountRegistrationData.apply)(AccountRegistrationData.unapply)
  )

  val userEditForm = Form(
    mapping(
      "userName" -> nonEmptyText,
      "fullName" -> optional(text),
      "mailAddress" -> email,
      "description" -> optional(text)
    )(AccountData.apply)(AccountData.unapply)
  )

  val updatePasswordForm = Form(
    mapping(
      "oldPassword" -> nonEmptyText,
      "newPassword" -> nonEmptyText
    )(PasswordData.apply)(PasswordData.unapply)
  )

  def login = Action { implicit request =>
    Ok(html.userLogin(loginForm))
  }

  def register = Action { implicit request =>
    Ok(html.userRegister(registerForm))
  }

  def saveUser = Action.async { implicit request =>
    val formValidatedFunction = { user: AccountRegistrationData =>
      accountService.findByLoginOrEmail(user.userName, user.mailAddress).flatMap {
        case None =>
          val acc = Account.buildNewAccount(user)
          accountService.insert(acc).map { accountId =>
            Redirect(routes.RepositoryController.list)
              .withSession(AuthController.SESSION_NAME -> accountId.get.toString)
          }
        case _ =>
          val formBuiltFromRequest = registerForm.bindFromRequest
          val newForm = registerForm.bindFromRequest.copy(
            errors = formBuiltFromRequest.errors ++ Seq(FormError("userName", "User name or email already exists."))
          )
          Future(BadRequest(html.userRegister(newForm)))
      }
    }

    registerForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(html.userRegister(formWithErrors))),
      formValidatedFunction
    )
  }

  trait AuthException
  class UserDoesNotExist extends Exception with AuthException
  class WrongPassword extends Exception with AuthException

  def authenticate = Action.async { implicit request =>
    loginForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(html.userLogin(formWithErrors))),
      user => {
        accountService
          .findByLoginOrEmail(user.userName)
          .flatMap {
            case Some(account) =>
              if (EncryptionService.checkHash(user.password, account.password)) {
                Future(
                  Redirect(routes.RepositoryController.list)
                    .withSession(AuthController.SESSION_NAME -> account.id.toString)
                )
              } else {
                throw new WrongPassword
              }
            case _ =>
              throw new UserDoesNotExist
          }
          .recover {
            case e: Exception with AuthException =>
              val formBuiltFromRequest = loginForm.bindFromRequest
              val newForm = loginForm.bindFromRequest.copy(
                errors = formBuiltFromRequest.errors ++ Seq(FormError("userName", "Check username of password"))
              )
              BadRequest(html.userLogin(newForm))
          }
      }
    )
  }

  def logout = Action {
    Redirect(routes.AuthController.login).withNewSession.flashing(
      "success" -> "You are now logged out."
    )
  }

  def profilePage = userAction { implicit request =>
    val filledForm = userEditForm.fill(
      AccountData(request.account.userName,
                  request.account.fullName,
                  request.account.mailAddress,
                  request.account.description)
    )
    Ok(html.userProfile(filledForm, updatePasswordForm))
  }

  def editProfile = userAction.async { implicit request =>
    userEditForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(html.userProfile(formWithErrors, updatePasswordForm))),
      accountData =>
        accountService.findByLoginOrEmail("", accountData.mailAddress).flatMap {
          case None =>
            Future(Ok(html.userProfile(userEditForm, updatePasswordForm)))
          case _ =>
            val formBuiltFromRequest = userEditForm.bindFromRequest
            val newForm = userEditForm.bindFromRequest.copy(
              errors = formBuiltFromRequest.errors ++ Seq(FormError("mailAddress", "Email already exists."))
            )
            Future(BadRequest(html.userProfile(newForm, updatePasswordForm)))
        })
  }

  def updatePassword = userAction.async { implicit request =>
    updatePasswordForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(html.userProfile(userEditForm, formWithErrors))),
      passwordData =>
        Redirect(routes.AuthController.profilePage()).flashing("success" -> s"Password successfully updated")
    )
  }
}

object AuthController {
  val SESSION_NAME = "user_id"
}
