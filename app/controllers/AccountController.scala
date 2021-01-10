package controllers

import actions.AuthenticatedRequest
import models._
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.i18n.Messages
import play.api.mvc._
import repositories.{ AccessDenied, AccountRepository, NotFoundInRepository, RzError }
import views._

import java.io.{ File, IOException }
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class AccountController @Inject() (
  accountService: AccountRepository,
  userAction: AuthenticatedRequest,
  config: Configuration,
  errorHandler: ErrorHandler,
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

  val signinForm: Form[AccountLoginData] = Form(
    mapping("userName" -> nonEmptyText, "password" -> nonEmptyText)(AccountLoginData.apply)(AccountLoginData.unapply)
  )

  val signupForm: Form[AccountRegistrationData] = Form(
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
      "id" -> nonEmptyText
    )(SshRemoveData.apply)(SshRemoveData.unapply)
  )

  val accountEditForm: Form[AccountData] = Form(
    mapping(
      "userName"    -> nonEmptyText,
      "fullName"    -> optional(text(maxLength = 25)),
      "mailAddress" -> email
    )(AccountData.apply)(AccountData.unapply)
  )

  val updatePasswordForm: Form[PasswordData] = Form(
    mapping(
      "oldPassword" -> nonEmptyText,
      "newPassword" -> nonEmptyText
    )(PasswordData.apply)(PasswordData.unapply)
  )

  def index: Action[AnyContent] =
    userAction.async(implicit request => Future(Redirect(routes.GitEntitiesController.list())))

  def signin: Action[AnyContent] = Action.async(implicit request => Future(Ok(html.signin(signinForm))))

  def signup: Action[AnyContent] = Action.async(implicit request => Future(Ok(html.signup(signupForm))))

  def logout: Action[AnyContent] = Action.async { implicit request =>
    Future(Redirect(routes.AccountController.signin()).withNewSession.flashing("success" -> Messages("logout")))
  }

  private def clearAccountData(data: Option[Map[String, Seq[String]]]): Map[String, Seq[String]] = {
    val form: Map[String, Seq[String]] = data.getOrElse(collection.immutable.Map[String, Seq[String]]())
    form.map {
      case (key, values) if key == "mailAddress" => (key, values.map(_.trim.toLowerCase()))
      case (key, values) if key == "userName"    => (key, values.map(_.trim.toLowerCase()))
      case (key, values) if key == "fullName"    => (key, values.map(_.trim.capitalize))
      case (key, values)                         => (key, values)
    }
  }

  def saveAccout: Action[AnyContent] = Action.async { implicit request =>
    val incomingData = request.body.asFormUrlEncoded
    val cleanData    = clearAccountData(incomingData)
    signupForm
      .bindFromRequest(cleanData)
      .fold(
        formWithErrors => Future(BadRequest(html.signup(formWithErrors))),
        (accountData: AccountRegistrationData) =>
          accountService.getByUsernameOrEmail(accountData.userName) match { // TODO accountData.email
            case Left(NotFoundInRepository) =>
              val acc      = new Account(accountData)
              val password = HashedString.fromString(accountData.password)
              accountService.set(acc, password)
              Future(Redirect(routes.GitEntitiesController.list()).withSession(SessionName.toString -> acc.id))
            case _ =>
              val formBuiltFromRequest = signupForm.bindFromRequest
              val newForm = signupForm.bindFromRequest.copy(
                errors =
                  formBuiltFromRequest.errors ++ Seq(FormError("userName", Messages("signup.error.alreadyexists")))
              )
              Future(BadRequest(html.signup(newForm)))
          }
      )
  }

  private def checkAccountPassword(userName: String, passwordHash: String): Either[RzError, Account] =
    accountService.getById(Account.id(userName)) match {
      case Right(account: Account) =>
        accountService.getPassword(account) match {
          case Right(password) if HashedString(password).check(passwordHash) => Right(account)
          case _                                                             => Left(AccessDenied)
        }
      case Left(e) => Left(e)
    }

  def authenticate: Action[AnyContent] = Action.async { implicit request: MessagesRequest[AnyContent] =>
    val incomingData = request.body.asFormUrlEncoded
    val cleanData    = clearAccountData(incomingData)
    signinForm
      .bindFromRequest(cleanData)
      .fold(
        formWithErrors => Future(BadRequest(html.signin(formWithErrors))),
        accountData =>
          checkAccountPassword(accountData.userName, accountData.password) match {
            case Right(account) =>
              Future(
                Redirect(routes.GitEntitiesController.list()).withSession(SessionName.toString -> account.id)
              )
            case _ =>
              val formBuiltFromRequest = signinForm.bindFromRequest
              val newForm = signinForm.bindFromRequest.copy(
                errors = formBuiltFromRequest.errors ++ Seq(FormError("userName", Messages("signin.error.wrongcred")))
              )
              Future(BadRequest(html.signin(newForm)))
          }
      )
  }

  private def filledAccountEditForm(account: Account): Form[AccountData] =
    accountEditForm.fill(
      AccountData(account.userName, Some(account.fullName), account.email)
    )

  def accountPage: Action[AnyContent] = userAction.async { implicit request =>
    accountService.getById(request.account.id) match {
      case Right(account) => Future(Ok(html.userProfile(filledAccountEditForm(account), updatePasswordForm)))
      case _              => Future(errorHandler.clientError(request, msg = request.messages("error.notfound")))
    }
  }

  private def isEmailAvailable(currentEmail: String, newEmail: String): Future[Boolean] =
    if (currentEmail != newEmail) {
      accountService.getById(newEmail) match { // TODO
        case Right(_) => Future(false)
        case _        => Future(true)
      }
    } else Future(true)

  def editAccount: Action[AnyContent] = userAction.async { implicit request =>
    accountEditForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(html.userProfile(formWithErrors, updatePasswordForm))),
      accountData =>
        isEmailAvailable(request.account.email, accountData.email).flatMap { available =>
          if (available) {
//            accountService.updateProfileInfo(request.account.id, accountData).flatMap { _ =>
            Future(Ok(html.userProfile(accountEditForm.bindFromRequest, updatePasswordForm)))
//            }
          } else {
            val formBuiltFromRequest = accountEditForm.bindFromRequest
            val newForm = accountEditForm.bindFromRequest.copy(
              errors = formBuiltFromRequest.errors ++ Seq(
                FormError("mailAddress", Messages("profile.error.emailalreadyexists"))
              )
            )
            Future(BadRequest(html.userProfile(newForm, updatePasswordForm)))
          }
        }
    )
  }

  def updatePassword(): Action[AnyContent] = userAction.async { implicit request: AccountRequest[AnyContent] =>
    updatePasswordForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(html.userProfile(filledAccountEditForm(request.account), formWithErrors))),
      passwordData =>
        accountService.getPassword(request.account) match {
          case Right(passwordHash: String) if (HashedString(passwordData.oldPassword).check(passwordHash)) => {
            val newPasswordHash = HashedString.fromString(passwordData.newPassword).toString
            accountService.setPassword(request.account, newPasswordHash)
            Future(
              Redirect(routes.AccountController.accountPage())
                .flashing("success" -> Messages("profile.flash.passupdated"))
            )
          }
          case _ => {
            val formBuiltFromRequest = updatePasswordForm.bindFromRequest
            val newForm = updatePasswordForm.bindFromRequest.copy(
              errors = formBuiltFromRequest.errors ++ Seq(
                FormError("oldPassword", Messages("profile.error.passisincorrect"))
              )
            )
            Future(BadRequest(html.userProfile(filledAccountEditForm(request.account), newForm)))
          }
        }
    )
  }

  val allowedContentTypes = List("image/jpeg", "image/png")

  trait FileUploadException
  class WrongContentType extends Exception with FileUploadException
  class ExceededMaxSize  extends Exception with FileUploadException

  def uploadAccountPicture: Action[MultipartFormData[play.api.libs.Files.TemporaryFile]] =
    userAction(parse.multipartFormData).async { implicit request =>
      val redirect = Redirect(routes.AccountController.accountPage())
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
            val t = Thumbnail.make(picture.ref, thumbSize, picturesDir.getAbsolutePath, request.account.userName)
            accountService.setPicture(request.account, t.name)
            Future(redirect.flashing("success" -> Messages("profile.picture.success")))
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

  def accountPicture(account: String): Action[AnyContent] = Action { implicit request =>
    val accountPicture = new java.io.File(picturesDir.toString + "/" + new Thumbnail(account, thumbSize).name)
    if (accountPicture.exists()) {
      val etag = MD5.fromString(accountPicture.lastModified().toString)

      if (request.headers.get(IF_NONE_MATCH).getOrElse("") == etag) {
        NotModified
      } else {
        Ok.sendFile(accountPicture).withHeaders(ETAG -> etag)
      }
    } else {
      NotFound
    }
  }

  def keysPage(): Action[AnyContent] = userAction.async { implicit request =>
    Future(Ok(html.sshKeys(accountService.listSshKeys(request.account), addSshKeyForm, deleteSshKeyForm)))
  }

  def addSshKey(): Action[AnyContent] = userAction.async { implicit request =>
    addSshKeyForm.bindFromRequest.fold(
      formWithErrors =>
        Future(Ok(html.sshKeys(accountService.listSshKeys(request.account), formWithErrors, deleteSshKeyForm))),
      sshKeyData =>
        accountService.cardinalitySshKey(request.account) match {
          case Right(c: Long) if c < maximumNumberPerUser =>
            val key = new SshKey(sshKeyData.publicKey, request.account)
            accountService.setSshKey(request.account, key)
            Future(
              Redirect(routes.AccountController.keysPage())
                .flashing("success" -> Messages("profile.ssh.notification.added"))
            )
          case _ =>
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
        Future(Ok(html.sshKeys(accountService.listSshKeys(request.account), addSshKeyForm, formWithErrors))),
      (sshKeyIdData: SshRemoveData) => {
        accountService.deleteSshKey(request.account, sshKeyIdData.id) // TODO: check ownership
        Future(
          Redirect(routes.AccountController.keysPage())
            .flashing("success" -> Messages("profile.ssh.notification.removed"))
        )
      }
    )
  }

  def removeAccountPicture(): Action[AnyContent] = userAction.async { implicit request =>
    val accountPicture =
      new java.io.File(picturesDir.toString + "/" + new Thumbnail(request.account.userName, thumbSize))
    accountPicture.delete()
    accountService.removePicture(request.account)
    Future(
      Redirect(routes.AccountController.accountPage())
        .flashing("success" -> Messages("profile.picture.delete.success"))
    )
  }
}
