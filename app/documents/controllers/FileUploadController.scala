package documents.controllers

import akka.stream.IOResult
import akka.stream.scaladsl.{ FileIO, Sink }
import akka.util.ByteString
import collaborators.models.Role
import documents.models.{ CommitFile, RepositoryRequest, RzPathUrl, RzRepository }
import documents.repositories.RzMetaGitRepository
import documents.services.GitService
import documents.validations.UploadForms._
import play.api.i18n.Messages
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart.FileInfo
import users.controllers.AuthenticatedAction
import views._

import java.io.File
import java.nio.file.{ Files, Path }
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class FileUploadController @Inject() (
  git: GitService,
  authAction: AuthenticatedAction,
  metaGitRepository: RzMetaGitRepository,
  cc: MessagesControllerComponents,
  repositoryAction: RepositoryAction
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  type FilePartHandler[A] = FileInfo => Accumulator[ByteString, FilePart[A]]

  def uploadPage(accountName: String, repositoryName: String, rev: String, path: String): Action[AnyContent] =
    authAction.andThen(repositoryAction.on(accountName, repositoryName, Role.Editor)).async { implicit request =>
      Future(Ok(html.repository.upload(uploadForm, rev, RzPathUrl.make(path).uri)))
    }

  /**
   * Uploads a multipart file as a POST request.
   *
   */
  def upload(accountName: String, repositoryName: String, rev: String = ""): Action[MultipartFormData[File]] =
    authAction(parse.multipartFormData(handleFilePartAsFile))
      .andThen(repositoryAction.on(accountName, repositoryName, Role.Editor))
      .async { implicit req: RepositoryRequest[MultipartFormData[File]] =>
        uploadForm
          .bindFromRequest()
          .fold(
            formWithErrors =>
              Future(
                BadRequest(
                  html.repository.upload(
                    formWithErrors,
                    formWithErrors.data.getOrElse("rev", RzRepository.defaultBranch),
                    formWithErrors.data.getOrElse("path", ".")
                  )
                )
              ),
            data => {
              val files  = req.body.files.map(filePart => CommitFile.fromFilePart(filePart, data.path))
              val branch = if (rev.nonEmpty) rev else RzRepository.defaultBranch

              for {
                _ <- commitFiles(files, branch, data.path)(req)
                _ <- metaGitRepository.updateRepo(req.repository)
              } yield Redirect(
                routes.FileViewController.emptyTree(accountName, repositoryName, rev)
              ).flashing("success" -> Messages("repository.upload.success"))
            }
          )
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

  private def commitFiles(files: Seq[CommitFile], rev: String, path: String)(
    implicit req: RepositoryRequest[_]
  ): Future[_] =
    git.commitFiles(
      req.repository,
      files,
      req.account,
      rev,
      Messages("repository.upload.message", files.length)
    )
}
