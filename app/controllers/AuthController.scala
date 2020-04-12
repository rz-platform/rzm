package controllers

import java.nio.file.Paths

import javax.inject.Inject
import models._
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.i18n.Messages
import play.api.libs.Files
import play.api.mvc._
import services.encryption._
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

  private val appHome       = config.get[String]("play.server.dir")
  private val maxStaticSize = config.get[String]("play.server.media.max_size_bytes").toInt

  val loginForm: Form[AccountLoginData] = Form(
    mapping("userName" -> nonEmptyText, "password" -> nonEmptyText)(AccountLoginData.apply)(AccountLoginData.unapply)
  )

  val registerForm: Form[AccountRegistrationData] = Form(
    mapping(
      "userName"    -> text(maxLength = 36).verifying(pattern("^[A-Za-z\\d_\\-]+$".r)),
      "fullName"    -> optional(text(maxLength = 36)),
      "password"    -> nonEmptyText(maxLength = 255),
      "mailAddress" -> email
    )(AccountRegistrationData.apply)(AccountRegistrationData.unapply)
  )

  val userEditForm: Form[AccountData] = Form(
    mapping(
      "userName"    -> nonEmptyText,
      "fullName"    -> optional(text(maxLength = 25)),
      "mailAddress" -> email,
      "description" -> optional(text(maxLength = 255))
    )(AccountData.apply)(AccountData.unapply)
  )

  val updatePasswordForm: Form[PasswordData] = Form(
    mapping(
      "oldPassword" -> nonEmptyText,
      "newPassword" -> nonEmptyText
    )(PasswordData.apply)(PasswordData.unapply)
  )

  def login: Action[AnyContent] = Action { implicit request =>
    Ok(html.userLogin(loginForm))
  }

  def register: Action[AnyContent] = Action { implicit request =>
    Ok(html.userRegister(registerForm))
  }

  def saveUser: Action[AnyContent] = Action.async { implicit request =>
    registerForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(html.userRegister(formWithErrors))),
      (user: AccountRegistrationData) =>
        accountService.findByLoginOrEmail(user.userName, user.mailAddress).flatMap {
          case None =>
            val acc = Account.buildNewAccount(user)
            accountService.insert(acc).map { accountId =>
              Redirect(routes.RepositoryController.list())
                .withSession(AuthController.SESSION_NAME -> accountId.get.toString)
            }
          case _ =>
            val formBuiltFromRequest = registerForm.bindFromRequest
            val newForm = registerForm.bindFromRequest.copy(
              errors = formBuiltFromRequest.errors ++ Seq(FormError("userName", Messages("signup.error.alreadyexists")))
            )
            Future(BadRequest(html.userRegister(newForm)))
        }
    )
  }

  trait AuthException
  class UserDoesNotExist extends Exception with AuthException
  class WrongPassword    extends Exception with AuthException

  def authenticate: Action[AnyContent] = Action.async { implicit request =>
    loginForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(html.userLogin(formWithErrors))),
      user => {
        accountService
          .findByLoginOrEmail(user.userName)
          .flatMap {
            case Some(account) =>
              if (EncryptionService.checkHash(user.password, account.password)) {
                Future(
                  Redirect(routes.RepositoryController.list())
                    .withSession(AuthController.SESSION_NAME -> account.id.toString)
                )
              } else {
                throw new WrongPassword
              }
            case _ =>
              throw new UserDoesNotExist
          }
          .recover {
            case _: AuthException =>
              val formBuiltFromRequest = loginForm.bindFromRequest
              val newForm = loginForm.bindFromRequest.copy(
                errors = formBuiltFromRequest.errors ++ Seq(FormError("userName", Messages("signin.error.wrongcred")))
              )
              BadRequest(html.userLogin(newForm))
          }
      }
    )
  }

  def logout: Action[AnyContent] = Action { implicit request =>
    Redirect(routes.AuthController.login()).withNewSession.flashing(
      "success" -> Messages("logout")
    )
  }

  private def filledProfileForm(account: Account): Form[AccountData] = {
    userEditForm.fill(
      AccountData(account.userName, Some(account.fullName), account.mailAddress, Some(account.description))
    )
  }

  def profilePage: Action[AnyContent] = userAction { implicit request =>
    Ok(html.userProfile(filledProfileForm(request.account), updatePasswordForm))
  }

  private def isEmailAvailable(currentEmail: String, newEmail: String): Future[Boolean] = {
    if (currentEmail != newEmail) {
      accountService.findByLoginOrEmail("", newEmail).flatMap {
        case Some(_) => Future(false)
        case None    => Future(true)
      }
    } else Future(true)
  }

  def editProfile: Action[AnyContent] = userAction.async { implicit request =>
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
              errors = formBuiltFromRequest.errors ++ Seq(
                FormError("mailAddress", Messages("profile.error.emailalreadyexists"))
              )
            )
            Future(BadRequest(html.userProfile(newForm, updatePasswordForm)))
          }
        }
      }
    )
  }

  def updatePassword(): Action[AnyContent] = userAction.async { implicit request =>
    updatePasswordForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(html.userProfile(filledProfileForm(request.account), formWithErrors))),
      passwordData => {
        if (EncryptionService.checkHash(passwordData.oldPassword, request.account.password)) {
          val newPasswordHash = EncryptionService.getHash(passwordData.newPassword)
          accountService.updatePassword(request.account.id, newPasswordHash).flatMap { _ =>
            Future(
              Redirect(routes.AuthController.profilePage()).flashing("success" -> Messages("profile.flash.passupdated"))
            )
          }
        } else {
          val formBuiltFromRequest = updatePasswordForm.bindFromRequest
          val newForm = updatePasswordForm.bindFromRequest.copy(
            errors = formBuiltFromRequest.errors ++ Seq(
              FormError("oldPassword", Messages("profile.error.passisincorrect"))
            )
          )
          Future(BadRequest(html.userProfile(filledProfileForm(request.account), newForm)))
        }
      }
    )
  }

  val allowedContentTypes = List("image/png", "image/jpeg")

  trait FileUploadException
  class WrongContentType extends Exception with FileUploadException
  class ExceededMaxSize  extends Exception with FileUploadException

  def uploadProfilePicture: Action[MultipartFormData[Files.TemporaryFile]] =
    Action(parse.multipartFormData) { implicit request =>
      val redirect = Redirect(routes.AuthController.profilePage())
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

            picture.ref.copyTo(Paths.get(s"$appHome/public/pictures/$filename"), replace = true)
            redirect.flashing("success" -> "Profile picture updated")
          } catch {
            case _: WrongContentType => redirect.flashing("error" -> Messages("profile.error.onlyimages"))
            case _: ExceededMaxSize  => redirect.flashing("error" -> Messages("profile.error.imagetoobig"))
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
