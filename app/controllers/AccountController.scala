package controllers

import actions.AuthenticatedAction
import forms.RzConstraints
import models._
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.i18n.Messages
import play.api.mvc._
import repositories._
import views._

import java.io.{ File, IOException }
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class AccountController @Inject() (
  accountRepository: AccountRepository,
  authAction: AuthenticatedAction,
  config: Configuration,
  errorHandler: ErrorHandler,
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

  val signupForm: Form[AccountRegistrationData] = Form(
    mapping(
      "userName"    -> text(maxLength = 36).verifying(pattern(RzRegex.onlyAlphabet)),
      "fullName"    -> optional(text(maxLength = 36)),
      "password"    -> nonEmptyText(maxLength = 255),
      "timezone"    -> nonEmptyText.verifying(RzConstraints.timeZoneConstraint),
      "mailAddress" -> email
    )(AccountRegistrationData.apply)(AccountRegistrationData.unapply)
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

  val timeZoneForm: Form[TimeZoneData] = Form(
    mapping(
      "timezone" -> nonEmptyText.verifying(RzConstraints.timeZoneConstraint)
    )(TimeZoneData.apply)(TimeZoneData.unapply)
  )

  private val zoneIds = TimezoneOffsetRepository.zoneIds

  def signup: Action[AnyContent] = Action.async(implicit request => Future(Ok(html.signup(signupForm, zoneIds))))

  private def clearAccountData(data: Option[Map[String, Seq[String]]]): Map[String, Seq[String]] = {
    val form: Map[String, Seq[String]] = data.getOrElse(collection.immutable.Map[String, Seq[String]]())
    form.map {
      case (key, values) if key == "mailAddress" => (key, values.map(_.trim.toLowerCase()))
      case (key, values) if key == "userName"    => (key, values.map(_.trim.toLowerCase()))
      case (key, values) if key == "fullName"    => (key, values.map(_.trim.capitalize))
      case (key, values)                         => (key, values)
    }
  }

  def saveAccount: Action[AnyContent] = Action.async { implicit request =>
    val incomingData = request.body.asFormUrlEncoded
    val cleanData    = clearAccountData(incomingData)
    signupForm
      .bindFromRequest(cleanData)
      .fold(
        formWithErrors => Future(BadRequest(html.signup(formWithErrors, zoneIds))),
        data =>
          accountRepository.getByUsernameOrEmail(data.userName).flatMap { // TODO accountData.email
            case Left(NotFoundInRepository) =>
              val account  = new Account(data)
              val password = HashedString.fromString(data.password)
              accountRepository
                .set(account, password)
                .flatMap(_ => authAction.authorize(account, request.session))
            case _ =>
              val formBuiltFromRequest = signupForm.bindFromRequest()
              val newForm = signupForm
                .bindFromRequest()
                .copy(
                  errors =
                    formBuiltFromRequest.errors ++ Seq(FormError("userName", Messages("signup.error.alreadyexists")))
                )
              Future(BadRequest(html.signup(newForm, zoneIds)))
          }
      )
  }

  private def filledAccountEditForm(account: Account): Form[AccountData] =
    accountEditForm.fill(
      AccountData(account.userName, Some(account.fullName), account.email)
    )

  def accountPage: Action[AnyContent] = authAction.async { implicit request =>
    accountRepository.getById(request.account.id).flatMap {
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
        (accountData: AccountData) =>
          isEmailAvailable(req.account.email, accountData.email).flatMap {
            case true =>
              accountRepository
                .update(req.account, req.account.fromForm(accountData))
                .map(_ => Ok(html.profile(accountEditForm.bindFromRequest(), updatePasswordForm, zoneIds)))
            case false =>
              val formBuiltFromRequest = accountEditForm.bindFromRequest()
              val newForm = accountEditForm
                .bindFromRequest()
                .copy(
                  errors = formBuiltFromRequest.errors ++ Seq(
                    FormError("mailAddress", Messages("profile.error.emailalreadyexists"))
                  )
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
          accountRepository
            .setTimezone(req.account, data.tz)
            .map(_ =>
              Redirect(routes.AccountController.accountPage())
                .flashing("success" -> Messages("profile.flash.tzupdated"))
            )
      )
  }

  def updatePassword(): Action[AnyContent] = authAction.async { implicit req =>
    updatePasswordForm
      .bindFromRequest()
      .fold(
        formWithErrors => Future(BadRequest(html.profile(filledAccountEditForm(req.account), formWithErrors, zoneIds))),
        passwordData =>
          accountRepository.getPassword(req.account).flatMap {
            case Right(passwordHash: String) if (HashedString(passwordHash).check(passwordData.newPassword)) =>
              val newPasswordHash = HashedString.fromString(passwordData.newPassword).toString
              accountRepository
                .setPassword(req.account, newPasswordHash)
                .map(_ =>
                  Redirect(routes.AccountController.accountPage())
                    .flashing("success" -> Messages("profile.flash.passupdated"))
                )
            case _ =>
              val formBuiltFromRequest = updatePasswordForm.bindFromRequest()
              val newForm = updatePasswordForm
                .bindFromRequest()
                .copy(
                  errors = formBuiltFromRequest.errors ++ Seq(
                    FormError("oldPassword", Messages("profile.error.passisincorrect"))
                  )
                )
              Future(BadRequest(html.profile(filledAccountEditForm(req.account), newForm, zoneIds)))
          }
      )
  }

  val allowedContentTypes = List("image/jpeg", "image/png")

  def uploadAccountPicture: Action[MultipartFormData[play.api.libs.Files.TemporaryFile]] =
    authAction(parse.multipartFormData).async { implicit request =>
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
            // TODO: Future
            val t = Thumbnail.make(picture.ref, thumbSize, picturesDir.getAbsolutePath, request.account.userName)
            accountRepository
              .setPicture(request.account, t.name)
              .map(_ => redirect.flashing("success" -> Messages("profile.picture.success")))
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

  def removeAccountPicture(): Action[AnyContent] = authAction.async { implicit req =>
    // TODO: put in Future
    val accountPicture =
      new java.io.File(picturesDir.toString + "/" + new Thumbnail(req.account.userName, thumbSize))
    accountPicture.delete()
    accountRepository
      .removePicture(req.account)
      .map(_ =>
        Redirect(routes.AccountController.accountPage())
          .flashing("success" -> Messages("profile.picture.delete.success"))
      )
  }
}
