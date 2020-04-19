package controllers

import java.io.{File, IOException}

import javax.inject.Inject
import models._
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.i18n.Messages
import play.api.mvc._
import services.encryption.EncryptionService.md5HashString
import services.encryption._
import services.images.ImageService._
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

  private val picturesDir: File  = new File(config.get[String]("play.server.media.pictures_path"))
  private val maxStaticSize: Int = config.get[String]("play.server.media.max_size_bytes").toInt
  private val thumbSize: Int     = config.get[String]("play.server.media.thumbnail_size").toInt

  if (!picturesDir.exists) {
    picturesDir.mkdirs()
  }

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

  val allowedContentTypes = List("image/jpeg", "image/png")

  trait FileUploadException
  class WrongContentType extends Exception with FileUploadException
  class ExceededMaxSize  extends Exception with FileUploadException

  def uploadProfilePicture: Action[MultipartFormData[play.api.libs.Files.TemporaryFile]] =
    userAction(parse.multipartFormData).async { implicit request =>
      val redirect = Redirect(routes.AuthController.profilePage())
      request.body
        .file("picture")
        .map { picture =>
          try {
            val contentType = picture.contentType.getOrElse { throw new WrongContentType }
            if (!(allowedContentTypes contains contentType)) {
              throw new WrongContentType
            }
            if (picture.fileSize > maxStaticSize) {
              throw new ExceededMaxSize
            }
            createSquaredThumbnails(picture.ref, thumbSize, picturesDir.getAbsolutePath, request.account.userName)
            accountService.hasPicture(request.account.id).flatMap { _ =>
              Future(redirect.flashing("success" -> Messages("profile.picture.success")))
            }
          } catch {
            case _: WrongContentType => Future(redirect.flashing("error" -> Messages("profile.error.onlyimages")))
            case _: ExceededMaxSize  => Future(redirect.flashing("error" -> Messages("profile.error.imagetoobig")))
            case _: IOException      => Future(redirect.flashing("error" -> Messages("profile.error.processing")))
          }
        }
        .getOrElse {
          Future(redirect.flashing("error" -> Messages("profile.error.missingpicture")))
        }
    }

  def profilePicture(account: String): Action[AnyContent] = Action { implicit request =>
    val profilePicture = new java.io.File(picturesDir.toString + "/" + thumbImageName(account, thumbSize))
    if (profilePicture.exists()) {
      val etag = md5HashString(profilePicture.lastModified().toString)

      if (request.headers.get(IF_NONE_MATCH).getOrElse("") == etag) {
        NotModified
      } else {
        Ok.sendFile(profilePicture).withHeaders(ETAG -> etag)
      }
    } else {
      NotFound
    }
  }

  def removeProfilePicture: Action[AnyContent] = userAction.async { implicit request =>
    val profilePicture = new java.io.File(picturesDir.toString + "/" + thumbImageName(request.account.userName, thumbSize))
    profilePicture.delete()
    accountService.removePicture(request.account.id).flatMap { _ =>
      Future(
        Redirect(routes.AuthController.profilePage()).flashing("success" -> Messages("profile.picture.delete.success"))
      )
    }
  }
}

object AuthController {
  val SESSION_NAME = "user_id"
}
