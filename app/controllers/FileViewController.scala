package controllers
import actions.{ AuthenticatedAction, RepositoryAction }
import akka.stream.scaladsl.StreamConverters
import forms.EditorForms.{ addNewItemForm, editorForm }
import models._
import play.api.http.HttpEntity
import play.api.i18n.Messages
import play.api.mvc._
import repositories._
import services.GitService
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
      git.rawFile(req.repository, rev, RzPathUrl.make(path).uri).flatMap {
        case Some(rawFile) =>
          val stream = StreamConverters.fromInputStream(() => rawFile.inputStream)
          Future(
            Result(
              header = ResponseHeader(200, Map.empty),
              body = HttpEntity.Streamed(stream, Some(rawFile.contentLength.toLong), Some(rawFile.contentType))
            )
          )
        case None => errorHandler.onClientError(req, msg = Messages("error.notfound"))
      }
    }

  def emptyTree(accountName: String, repositoryName: String, rev: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Viewer)).async { implicit req =>
      metaGitRepository.getRzRepoLastFile(req.account, req.repository).flatMap {
        case Some(path) =>
          Future(
            Redirect(
              routes.FileViewController.blob(req.repository.owner.userName, req.repository.name, rev, path)
            )
          )
        case _ =>
          for {
            fileTree <- git.fileTree(req.repository, rev)
          } yield Ok(
            html.repository.view(
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
      _ <- metaGitRepository.setRzRepoLastFile(req.account, req.repository, path)
    } yield Ok(
      html.repository.view(
        editorForm.fill(
          EditedItem(
            blob.content.content.getOrElse(""),
            rev,
            path,
            rzPath.nameWithoutPath
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
        blobInfo <- git.blobFile(req.repository, rzPath.uri, rev)
        fileTree <- git.fileTree(req.repository, rev)
      } yield blobInfo match {
        case Some(blob: Blob) => renderBlob(blob, fileTree, rev, path, rzPath)
        case None             => errorHandler.onClientError(req, msg = Messages("error.notfound"))
      }
      result.flatten
    }

}
