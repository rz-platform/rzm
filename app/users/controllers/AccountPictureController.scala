package users.controllers

import authentication.models.MD5
import play.api.Configuration
import play.api.i18n.Messages
import play.api.libs.Files
import play.api.mvc._
import users.errors.{ ExceededMaxSize, ImageProcessingError, WrongContentType }
import users.models.{ AccountRequest, Thumbnail }
import users.repositories.AccountRepository

import java.io.File
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class AccountPictureController @Inject() (
  accountRepository: AccountRepository,
  authAction: AuthenticatedAction,
  config: Configuration,
  cc: MessagesControllerComponents
)(
  implicit ec: ExecutionContext
) extends MessagesAbstractController(cc) {
  val allowedContentTypes = List("image/jpeg", "image/png")

  private val picturesDir: File = new File(config.get[String]("play.server.media.pictures_path"))

  if (!picturesDir.exists) {
    picturesDir.mkdirs()
  }

  private val maxStaticSize: Int = config.get[String]("play.server.media.max_size_bytes").toInt
  private val thumbSize: Int     = config.get[String]("play.server.media.thumbnail_size").toInt

  def accountPicture(account: String): Action[AnyContent] = Action.async { implicit request =>
    val picturePath = accountPictureFile(account)

    if (picturePath.exists()) {
      val etag = MD5.fromString(picturePath.lastModified().toString)

      if (request.headers.get(IF_NONE_MATCH).getOrElse("") == etag) {
        Future(NotModified)
      } else {
        Future(Ok.sendFile(picturePath).withHeaders(ETAG -> etag))
      }
    } else {
      Future(NotFound)
    }
  }

  def uploadAccountPicture: Action[MultipartFormData[play.api.libs.Files.TemporaryFile]] =
    authAction(parse.multipartFormData).async {
      implicit request: AccountRequest[MultipartFormData[Files.TemporaryFile]] =>
        val redirect = Redirect(routes.AccountController.accountPage())
        request.body
          .file("picture")
          .map { picture =>
            checkImage(picture) match {
              case None =>
                for {
                  thumbnail <- makeThumbnail(picture)(request)
                  _         <- accountRepository.setPicture(request.account, thumbnail.name)
                } yield redirect.flashing("success" -> Messages("profile.picture.success"))

              case Some(WrongContentType) => Future(redirect.flashing("error" -> Messages("profile.error.onlyimages")))
              case Some(ExceededMaxSize)  => Future(redirect.flashing("error" -> Messages("profile.error.imagetoobig")))
            }
          }
          .getOrElse {
            Future(redirect.flashing("error" -> Messages("profile.error.missingpicture")))
          }
    }

  def removeAccountPicture(): Action[AnyContent] = authAction.async { implicit req =>
    for {
      _ <- deleteProfileImage(req.account.username)
      _ <- accountRepository.removePicture(req.account)
    } yield Redirect(routes.AccountController.accountPage())
      .flashing("success" -> Messages("profile.picture.delete.success"))
  }

  private def checkImage(picture: MultipartFormData.FilePart[Files.TemporaryFile]): Option[ImageProcessingError] = {
    val contentType = picture.contentType.getOrElse("")
    picture match {
      case _ if picture.fileSize > maxStaticSize            => Some(ExceededMaxSize)
      case _ if !(allowedContentTypes contains contentType) => Some(WrongContentType)
      case _                                                => Option.empty[ImageProcessingError]
    }
  }

  private def makeThumbnail(
    picture: MultipartFormData.FilePart[Files.TemporaryFile]
  )(req: AccountRequest[_]): Future[Thumbnail] = Future {
    Thumbnail.make(picture.ref, thumbSize, picturesDir.getAbsolutePath, req.account.username)
  }

  private def accountPictureFile(account: String): File = {
    val thumbName = new Thumbnail(account, thumbSize).name
    new java.io.File(picturesDir.toString + "/" + thumbName)
  }

  private def deleteProfileImage(account: String): Future[_] = Future {
    val picturePath = accountPictureFile(account)
    if (picturePath.exists()) {
      picturePath.delete()
    }
  }

}
