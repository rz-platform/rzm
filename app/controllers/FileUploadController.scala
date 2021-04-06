package controllers

import actions.{ AuthenticatedAction, RepositoryAction }
import akka.stream.IOResult
import akka.stream.scaladsl.{ FileIO, Sink }
import akka.util.ByteString
import models.{ RepositoryRequest, _ }
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.Messages
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart.FileInfo
import repositories._
import views._

import java.io.File
import java.nio.file.{ Files, Path }
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class FileUploadController @Inject() (
  git: GitRepository,
  authAction: AuthenticatedAction,
  cc: MessagesControllerComponents,
  repositoryAction: RepositoryAction
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {
  type FilePartHandler[A] = FileInfo => Accumulator[ByteString, FilePart[A]]

  val uploadFileForm: Form[UploadFileForm] = Form(
    mapping("path" -> text)(UploadFileForm.apply)(UploadFileForm.unapply)
  )

  def uploadPage(accountName: String, repositoryName: String, rev: String, path: String): Action[AnyContent] =
    authAction.andThen(repositoryAction.on(accountName, repositoryName, EditAccess)).async { implicit request =>
      Future(Ok(html.git.uploadFile(uploadFileForm, rev, RzPathUrl.make(path).uri)))
    }

  /**
   * Uses a custom FilePartHandler to return a type of "File" rather than
   * using Play's TemporaryFile class.  Deletion must happen explicitly on
   * completion, rather than TemporaryFile (which uses finalization to
   * delete temporary files).
   *
   * @return
   */
  private def handleFilePartAsFile: FilePartHandler[File] = {
    case FileInfo(partName, filename, contentType, _) =>
      val path: Path                                     = Files.createTempFile("multipartBody", "tempFile")
      val fileSink: Sink[ByteString, Future[IOResult]]   = FileIO.toPath(path)
      val accumulator: Accumulator[ByteString, IOResult] = Accumulator(fileSink)
      accumulator.map {
        case IOResult(_, _) =>
          FilePart(partName, filename, contentType, path.toFile)
      }
  }

  /**
   * Uploads a multipart file as a POST request.
   *
   */
  def upload(accountName: String, repositoryName: String, rev: String = ""): Action[MultipartFormData[File]] =
    authAction(parse.multipartFormData(handleFilePartAsFile))
      .andThen(repositoryAction.on(accountName, repositoryName, EditAccess)) {
        implicit req: RepositoryRequest[MultipartFormData[File]] =>
          uploadFileForm.bindFromRequest.fold(
            formWithErrors =>
              BadRequest(
                html.git.uploadFile(
                  formWithErrors,
                  formWithErrors.data.getOrElse("rev", RzRepository.defaultBranch),
                  formWithErrors.data.getOrElse("path", ".")
                )
              ),
            (data: UploadFileForm) => {
              val files = req.body.files.map(filePart => CommitFile.fromFilePart(filePart, data.path))
              git.commitFiles(
                req.repository,
                files,
                req.account,
                if (rev.nonEmpty) rev else RzRepository.defaultBranch,
                data.path,
                Messages("repository.upload.message", files.length)
              )
              Redirect(
                routes.FileTreeController.emptyTree(accountName, repositoryName, rev)
              ).flashing("success" -> Messages("repository.upload.success"))
            }
          )
      }
}
