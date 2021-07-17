package documents.controllers

import akka.stream.scaladsl.StreamConverters
import collaborators.models.Role
import documents.models._
import documents.repositories.RzMetaGitRepository
import documents.services.GitService
import documents.validations.EditorForms.{ addNewItemForm, editorForm }
import infrastructure.controllers.ErrorHandler
import play.api.http.HttpEntity
import play.api.i18n.Messages
import play.api.mvc._
import users.controllers.AuthenticatedAction
import views._

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class FileViewController @Inject() (
  git: GitService,
  errorHandler: ErrorHandler,
  metaGitRepository: RzMetaGitRepository,
  authAction: AuthenticatedAction,
  repoAction: RepositoryAction,
  cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  private val logger = play.api.Logger(this.getClass)

  def raw(accountName: String, repositoryName: String, rev: String, path: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Viewer)).async { implicit req =>
      git.rawFile(req.doc, rev, RzPathUrl.make(path).uri).flatMap {
        case Some(rawFile) =>
          val stream = StreamConverters.fromInputStream(() => rawFile.inputStream)
          Future(
            Result(
              header = ResponseHeader(200, Map.empty),
              body = HttpEntity.Streamed(stream, Some(rawFile.contentLength.toLong), Some(rawFile.contentType))
            ).withHeaders(s"Content-Disposition" -> "attachment;")
          )
        case None => errorHandler.onClientError(req, msg = Messages("error.notfound"))
      }
    }

  def emptyTree(accountName: String, repositoryName: String, rev: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Viewer)).async { implicit req =>
      metaGitRepository.getRzRepoLastFile(req.account, req.doc).flatMap {
        case Some(path) =>
          Future(
            Redirect(
              routes.FileViewController.blob(req.doc.owner.username, req.doc.name, rev, path)
            )
          )
        case _ =>
          for {
            fileTree <- git.fileTree(req.doc, rev)
          } yield Ok(
            html.document.view(
              editorForm,
              EmptyBlob,
              "",
              rev,
              FilePath(Array()),
              fileTree,
              addNewItemForm.fill(NewItem("", rev, "", isFolder = false))
            )
          )
      }
    }

  private def renderBlob(blob: Blob, fileTree: FileTree, rev: String, path: String, rzPath: RzPathUrl)(
    implicit req: RepositoryRequest[AnyContent]
  ): Future[Result] =
    for {
      _ <- metaGitRepository.setRzRepoLastFile(LastOpenedFile.asEntity(req.account, req.doc, path))
    } yield Ok(
      html.document.view(
        editorForm.fill(
          EditedItem(
            blob.content.content.getOrElse(""),
            rev,
            path,
            Some(rzPath.nameWithoutPath)
          )
        ),
        blob,
        rzPath.uri,
        rev,
        new FilePath(path),
        fileTree,
        addNewItemForm.fill(NewItem("", rev, "", isFolder = false))
      )
    ).withHeaders("Turbolinks-Location" -> req.uri)

  def blob(accountName: String, repositoryName: String, rev: String, path: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Viewer)).async { implicit req =>
      val rzPath: RzPathUrl = RzPathUrl.make(path)
      val result = for {
        blobInfo <- git.blobFile(req.doc, rzPath.uri, rev)
        fileTree <- git.fileTree(req.doc, rev)
      } yield blobInfo match {
        case Some(blob: Blob) => renderBlob(blob, fileTree, rev, path, rzPath)
        case None             => errorHandler.onClientError(req, msg = Messages("error.notfound"))
      }
      result.flatten
    }

}
