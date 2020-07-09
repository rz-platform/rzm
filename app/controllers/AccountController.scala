package controllers

import java.io.{ File, IOException }

import actions.AuthenticatedRequest
import javax.inject.Inject
import models._
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.i18n.Messages
import play.api.mvc._
import repositories.AccountRepository
import views._

import scala.concurrent.{ ExecutionContext, Future }

class AccountController @Inject() (
  accountService: AccountRepository,
  userAction: AuthenticatedRequest,
  config: Configuration,
  cc: MessagesControllerComponents
)(
  implicit ec: ExecutionContext
) extends MessagesAbstractController(cc) {

  private val logger = play.api.Logger(this.getClass)

  private val picturesDir: File         = new File(config.get[String]("play.server.media.pictures_path"))
  private val maxStaticSize: Int        = config.get[String]("play.server.media.max_size_bytes").toInt
  private val thumbSize: Int            = config.get[String]("play.server.media.thumbnail_size").toInt
  private val maximumNumberPerUser: Int = config.get[String]("play.server.ssh.maximumNumberPerUser").toInt

  if (!picturesDir.exists) {
    picturesDir.mkdirs()
  }

  val loginForm: Form[AccountLoginData] = Form(
    mapping("userName" -> nonEmptyText, "password" -> nonEmptyText)(AccountLoginData.apply)(AccountLoginData.unapply)
  )

  val registerForm: Form[AccountRegistrationData] = Form(
    mapping(
      "userName"    -> text(maxLength = 36).verifying(pattern(AccountNameRegex.toRegex)),
      "fullName"    -> optional(text(maxLength = 36)),
      "password"    -> nonEmptyText(maxLength = 255),
      "mailAddress" -> email
    )(AccountRegistrationData.apply)(AccountRegistrationData.unapply)
  )

  val addSshKeyForm: Form[SshKeyData] = Form(
    mapping(
      "publicKey" -> nonEmptyText.verifying(
        pattern(PublicKeyRegex.toRegex)
      )
    )(SshKeyData.apply)(SshKeyData.unapply)
  )
  val deleteSshKeyForm: Form[SshRemoveData] = Form(
    mapping(
      "id" -> number(min = 0)
    )(SshRemoveData.apply)(SshRemoveData.unapply)
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

  def index(): Action[AnyContent] = userAction(implicit request => Redirect(routes.GitEntitiesController.list()))

  def login: Action[AnyContent] = Action(implicit request => Ok(html.userLogin(loginForm)))

  def register: Action[AnyContent] = Action(implicit request => Ok(html.userRegister(registerForm)))

  def saveUser: Action[AnyContent] = Action.async { implicit request =>
    registerForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(html.userRegister(formWithErrors))),
      (userData: AccountRegistrationData) =>
        accountService.getByLoginOrEmail(userData.userName, userData.email).flatMap {
          case None =>
            val acc = RichAccount.fromScratch(userData)
            accountService.insert(acc).map { accountId =>
              Redirect(routes.GitEntitiesController.list())
                .withSession(SessionName.toString -> accountId.get.toString)
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

  def authenticate: Action[AnyContent] = Action.async { implicit request =>
    loginForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(html.userLogin(formWithErrors))),
      user =>
        accountService
          .getRichModelByLoginOrEmail(user.userName)
          .flatMap {
            case Some(account) =>
              if (HashedString(account.password).check(user.password)) {
                Future(
                  Redirect(routes.GitEntitiesController.list())
                    .withSession(SessionName.toString -> account.id.toString)
                )
              } else {
                throw new AuthException.WrongPassword
              }
            case _ =>
              throw new AuthException.UserDoesNotExist
          }
          .recover {
            case _: AuthException =>
              val formBuiltFromRequest = loginForm.bindFromRequest
              val newForm = loginForm.bindFromRequest.copy(
                errors = formBuiltFromRequest.errors ++ Seq(FormError("userName", Messages("signin.error.wrongcred")))
              )
              BadRequest(html.userLogin(newForm))
          }
    )
  }

  def logout: Action[AnyContent] = Action { implicit request =>
    Redirect(routes.AccountController.login()).withNewSession.flashing("success" -> Messages("logout"))
  }

  private def filledProfileForm(account: RichAccount): Form[AccountData] =
    userEditForm.fill(
      AccountData(account.userName, Some(account.fullName), account.email, Some(account.description))
    )

  def profilePage: Action[AnyContent] = userAction.async { implicit request =>
    accountService
      .getRichModelById(request.account.id)
      .map(account => Ok(html.userProfile(filledProfileForm(account), updatePasswordForm)))
  }

  private def isEmailAvailable(currentEmail: String, newEmail: String): Future[Boolean] =
    if (currentEmail != newEmail) {
      accountService.getByLoginOrEmail("", newEmail).flatMap {
        case Some(_) => Future(false)
        case None    => Future(true)
      }
    } else Future(true)

  def editProfile: Action[AnyContent] = userAction.async { implicit request =>
    userEditForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(html.userProfile(formWithErrors, updatePasswordForm))),
      accountData =>
        isEmailAvailable(request.account.email, accountData.email).flatMap { available =>
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
    )
  }

  def updatePassword(): Action[AnyContent] = userAction.async { implicit request =>
    updatePasswordForm.bindFromRequest.fold(
      formWithErrors =>
        accountService
          .getRichModelById(request.account.id)
          .flatMap(account => Future(BadRequest(html.userProfile(filledProfileForm(account), formWithErrors)))),
      passwordData =>
        accountService
          .getRichModelById(request.account.id)
          .flatMap { account =>
            if (HashedString(passwordData.oldPassword).check(account.password)) {
              val newPasswordHash = HashedString.fromString(passwordData.newPassword).toString
              accountService.updatePassword(request.account.id, newPasswordHash).flatMap { _ =>
                Future(
                  Redirect(routes.AccountController.profilePage())
                    .flashing("success" -> Messages("profile.flash.passupdated"))
                )
              }
            } else {
              val formBuiltFromRequest = updatePasswordForm.bindFromRequest
              val newForm = updatePasswordForm.bindFromRequest.copy(
                errors = formBuiltFromRequest.errors ++ Seq(
                  FormError("oldPassword", Messages("profile.error.passisincorrect"))
                )
              )
              Future(BadRequest(html.userProfile(filledProfileForm(account), newForm)))
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
      val redirect = Redirect(routes.AccountController.profilePage())
      request.body
        .file("picture")
        .map { picture =>
          try {
            val contentType = picture.contentType.getOrElse(throw new WrongContentType)
            if (!(allowedContentTypes contains contentType)) {
              throw new WrongContentType
            }
            if (picture.fileSize > maxStaticSize) {
              throw new ExceededMaxSize
            }
            Thumbnail.fromSource(picture.ref, thumbSize, picturesDir.getAbsolutePath, request.account.userName)
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
    val profilePicture = new java.io.File(picturesDir.toString + "/" + Thumbnail(account, thumbSize).name)
    if (profilePicture.exists()) {
      val etag = MD5.fromString(profilePicture.lastModified().toString)

      if (request.headers.get(IF_NONE_MATCH).getOrElse("") == etag) {
        NotModified
      } else {
        Ok.sendFile(profilePicture).withHeaders(ETAG -> etag)
      }
    } else {
      NotFound
    }
  }

  def keysPage(): Action[AnyContent] = userAction.async { implicit request =>
    accountService
      .userSshKeys(request.account.id)
      .flatMap(keys => Future(Ok(html.sshKeys(keys, addSshKeyForm, deleteSshKeyForm))))
  }

  def addSshKey(): Action[AnyContent] = userAction.async { implicit request =>
    addSshKeyForm.bindFromRequest.fold(
      formWithErrors =>
        accountService
          .userSshKeys(request.account.id)
          .flatMap(keys => Future(Ok(html.sshKeys(keys, formWithErrors, deleteSshKeyForm)))),
      sshKeyData =>
        accountService.numberOfUserSshKeys(request.account.id).flatMap {
          case n if n < maximumNumberPerUser =>
            accountService.insertSshKey(request.account.id, sshKeyData.publicKey).flatMap { _ =>
              Future(
                Redirect(routes.AccountController.keysPage())
                  .flashing("success" -> Messages("profile.ssh.notification.added"))
              )
            }
          case n if n >= maximumNumberPerUser =>
            Future(
              Redirect(routes.AccountController.keysPage())
                .flashing("error" -> Messages("profile.ssh.notification.toomuch"))
            )
        }
    )
  }

  def deleteSshKey(): Action[AnyContent] = userAction.async { implicit request =>
    deleteSshKeyForm.bindFromRequest.fold(
      formWithErrors =>
        accountService
          .userSshKeys(request.account.id)
          .flatMap(keys => Future(Ok(html.sshKeys(keys, addSshKeyForm, formWithErrors)))),
      sshKeyIdData =>
        accountService.deleteSshKeys(request.account.id, sshKeyIdData.id).flatMap { _ =>
          Future(
            Redirect(routes.AccountController.keysPage())
              .flashing("success" -> Messages("profile.ssh.notification.removed"))
          )
        }
    )
  }

  def removeProfilePicture(): Action[AnyContent] = userAction.async { implicit request =>
    val profilePicture =
      new java.io.File(picturesDir.toString + "/" + Thumbnail(request.account.userName, thumbSize))
    profilePicture.delete()
    accountService.removePicture(request.account.id).flatMap { _ =>
      Future(
        Redirect(routes.AccountController.profilePage())
          .flashing("success" -> Messages("profile.picture.delete.success"))
      )
    }
  }
}
