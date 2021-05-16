package controllers
import actions.{ AuthenticatedAction, RepositoryAction }
import akka.stream.scaladsl.StreamConverters
import forms.EditorForms.{ addNewItemForm, editorForm }
import models._
import play.api.http.HttpEntity
import play.api.i18n.Messages
import play.api.mvc._
import repositories._
import views._

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class FileViewController @Inject() (
  git: GitRepository,
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
      val raw = git.getRawFile(req.repository, rev, RzPathUrl.make(path).uri)

      raw match {
        case Some(rawFile) =>
          val stream = StreamConverters.fromInputStream(() => rawFile.inputStream)
          Future.successful {
            Result(
              header = ResponseHeader(200, Map.empty),
              body = HttpEntity.Streamed(stream, Some(rawFile.contentLength.toLong), Some(rawFile.contentType))
            )
          }
        case None => errorHandler.onClientError(req, msg = Messages("error.notfound"))
      }
    }

  def emptyTree(accountName: String, repositoryName: String, rev: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Viewer)).async { implicit req =>
      metaGitRepository.getRzRepoLastFile(req.account, req.repository).map {
        case Some(path) =>
          Redirect(
            routes.FileViewController.blob(req.repository.owner.userName, req.repository.name, rev, path)
          )
        case _ =>
          val fileTree = git.fileTree(req.repository, rev)
          Ok(
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

  def blob(accountName: String, repositoryName: String, rev: String, path: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Viewer)).async { implicit req =>
      val rzPath   = RzPathUrl.make(path)
      val blobInfo = git.blobFile(req.repository, rzPath.uri, rev)
      val fileTree = git.fileTree(req.repository, rev)
      blobInfo match {
        case Some(blob) =>
          metaGitRepository.setRzRepoLastFile(req.account, req.repository, path)
          Future.successful {
            Ok(
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
          }
        case None => errorHandler.onClientError(req, msg = Messages("error.notfound"))
      }
    }

}
