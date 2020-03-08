package controllers

import java.nio.file.Paths

import scala.util.{Failure, Success, Try}
import javax.inject.Inject
import models.{Account, AccountData, AccountLoginData, AccountRegistrationData, AccountRepository, PasswordData}
import play.api.Configuration
import services.encryption._
import play.api.data.Forms._
import play.api.data._
import play.api.mvc._
import views._

import scala.concurrent.{ExecutionContext, Future}

class AuthController @Inject() (
  accountService: AccountRepository,
  userAction: UserInfoAction,
  config: Configuration,
  cc: MessagesControllerComponents
)(
  implicit ec: ExecutionContext
) extends MessagesAbstractController(cc) {

  private val logger = play.api.Logger(this.getClass)

  private val appHome = config.get[String]("play.server.dir")
  private val maxStaticSize = config.get[String]("play.server.media.max_size_bytes").toInt

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

  private def filledProfileForm(account: Account): Form[AccountData] = {
    userEditForm.fill(
      AccountData(account.userName, Some(account.fullName), account.mailAddress, Some(account.description))
    )
  }

  def profilePage = userAction { implicit request =>
    Ok(html.userProfile(filledProfileForm(request.account), updatePasswordForm))
  }

  private def isEmailAvailable(currentEmail: String, newEmail: String): Future[Boolean] = {
    if (currentEmail != newEmail) {
      accountService.findByLoginOrEmail("", newEmail).flatMap {
        case Some(_) => Future(false)
        case None => Future(true)
      }
    } else Future(true)
  }

  def editProfile = userAction.async { implicit request =>
    userEditForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(html.userProfile(formWithErrors, updatePasswordForm))),
      accountData => {
        isEmailAvailable(request.account.mailAddress, accountData.mailAddress).flatMap { available =>
          if (available) {
            accountService.updateProfileInfo(request.account.id, accountData).flatMap { _ =>
              Future(Ok(html.userProfile(userEditForm.bindFromRequest, updatePasswordForm)))
            }
          } else {
            val formBuiltFromRequest = userEditForm.bindFromRequest
            val newForm = userEditForm.bindFromRequest.copy(
              errors = formBuiltFromRequest.errors ++ Seq(FormError("mailAddress", "Email already exists."))
            )
            Future(BadRequest(html.userProfile(newForm, updatePasswordForm)))
          }
        }
      }
    )
  }

  def updatePassword = userAction.async { implicit request =>
    updatePasswordForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(html.userProfile(filledProfileForm(request.account), formWithErrors))),
      passwordData => {
        if (EncryptionService.checkHash(passwordData.oldPassword, request.account.password)) {
          val newPasswordHash = EncryptionService.getHash(passwordData.newPassword)
          accountService.updatePassword(request.account.id, newPasswordHash).flatMap { _ =>
            Future(
              Redirect(routes.AuthController.profilePage()).flashing("success" -> s"Password successfully updated")
            )
          }
        } else {
          val formBuiltFromRequest = updatePasswordForm.bindFromRequest
          val newForm = updatePasswordForm.bindFromRequest.copy(
            errors = formBuiltFromRequest.errors ++ Seq(FormError("oldPassword", "Current password is incorrect."))
          )
          Future(BadRequest(html.userProfile(filledProfileForm(request.account), newForm)))
        }
      }
    )
  }

  val allowedContentTypes = List("image/png", "image/jpeg")

  trait FileUploadException
  class WrongContentType extends Exception("Only images is allowed") with FileUploadException
  class ExceededMaxSize extends Exception("Image bigger than a max size") with FileUploadException

  def uploadProfilePicture = Action(parse.multipartFormData) { request =>
    val redirect = Redirect(routes.AuthController.profilePage)
    request.body
      .file("picture")
      .map { picture =>
        // only get the last part of the filename
        // otherwise someone can send a path like ../../home/foo/bar.txt to write to other files on the system
        val filename = Paths.get(picture.filename).getFileName
        val fileSize = picture.fileSize
        try {
          val contentType = picture.contentType.getOrElse { throw new WrongContentType }

          if (!(allowedContentTypes contains contentType)) {
            throw new WrongContentType
          }

          if (fileSize > maxStaticSize) {
            throw new ExceededMaxSize
          }

          picture.ref.copyTo(Paths.get(s"${appHome}/public/pictures/$filename"), replace = true)
          redirect.flashing("success" -> "Profile picture updated")
        } catch {
          case exc: FileUploadException => redirect.flashing("error" -> exc.getMessage)
        }
      }
      .getOrElse {
        redirect.flashing("error" -> "Missing file")
      }
  }
}

object AuthController {
  val SESSION_NAME = "user_id"
}
