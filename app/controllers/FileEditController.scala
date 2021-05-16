package controllers

import actions.{ AuthenticatedAction, RepositoryAction }
import forms.EditorForms._
import models.{ RepositoryRequest, _ }
import org.eclipse.jgit.lib.{ Constants, FileMode }
import play.api.data.Form
import play.api.i18n.Messages
import play.api.mvc._
import repositories._
import views._

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class FileEditController @Inject() (
  git: GitRepository,
  metaGitRepository: RzMetaGitRepository,
  authAction: AuthenticatedAction,
  repoAction: RepositoryAction,
  cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {
  private val logger = play.api.Logger(this.getClass)

  def edit(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Editor)).async { implicit req =>
      val cleanData = clean(req.body.asFormUrlEncoded)
      editorForm
        .bindFromRequest(cleanData)
        .fold(
          formWithErrors => {
            val rev  = formWithErrors.data.getOrElse("rev", RzRepository.defaultBranch)
            val path = formWithErrors.data.get("path")
            path match {
              case Some(path) =>
                Future(Redirect(routes.FileViewController.blob(accountName, repositoryName, rev, path)))
              case None => Future(Redirect(routes.FileViewController.emptyTree(accountName, repositoryName, rev)))
            }
          },
          edited => {
            val oldPath = RzPathUrl.make(edited.path)
            val newPath = RzPathUrl.make(oldPath.pathWithoutFilename, edited.name, isFolder = false)
            val content = if (edited.content.nonEmpty) edited.content.getBytes() else Array.emptyByteArray

            for {
              _ <- saveChanges(edited.rev, oldPath, newPath, content)(req)
              _ <- metaGitRepository.updateRepo(req.repository)
            } yield Redirect(
              routes.FileViewController.blob(accountName, repositoryName, edited.rev, newPath.encoded)
            )
          }
        )
    }

  def addNewItemPage(
    accountName: String,
    repositoryName: String,
    rev: String,
    path: String,
    isFolder: Boolean = false
  ): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Editor)).async { implicit req =>
      Future(Ok(html.repository.newfile(addNewItemForm, rev, RzPathUrl.make(path).uri, isFolder)))
    }

  // Play cannot reverse url with boolean flag, so we do need separate method for folders
  def addNewFolderPage(
    accountName: String,
    repositoryName: String,
    rev: String,
    path: String
  ): Action[AnyContent] =
    addNewItemPage(accountName, repositoryName, rev, path, isFolder = true)

  def addNewItem(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Editor)).async { implicit req =>
      val cleanData = clean(req.body.asFormUrlEncoded)
      addNewItemForm
        .bindFromRequest(cleanData)
        .fold(
          formWithErrors => errorFunc(formWithErrors, accountName, repositoryName),
          newItem => {
            val fName: RzPathUrl = RzPathUrl.make(newItem.path, newItem.name, newItem.isFolder)

            for {
              _ <- commitNewFile(fName, newItem)(req)
              _ <- metaGitRepository.updateRepo(req.repository)
            } yield Redirect(
              routes.FileViewController.blob(accountName, repositoryName, newItem.rev, fName.encoded)
            )
          }
        )
    }

  private def saveChanges(
    rev: String,
    oldPath: RzPathUrl,
    newPath: RzPathUrl,
    content: Array[Byte]
  )(req: RepositoryRequest[AnyContent]): Future[_] = Future {
    git.commitFiles(
      req.repository,
      rev,
      oldPath.pathWithoutFilename,
      req.messages("repository.viewFile.commitMessage", oldPath.nameWithoutPath),
      req.account
    ) {
      case (git_, headTip, builder, inserter) =>
        val permission = git
          .processTree(git_, headTip) { (path, tree) =>
            // Add all entries except the editing file
            if (!newPath.uri.contains(path) && !oldPath.uri.contains(path)) {
              builder.add(git.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId))
            }
            // Retrieve permission if file exists to keep it
            oldPath.uri.collect { case x if x.toString == path => tree.getEntryFileMode.getBits }
          }
          .flatten
          .headOption

        builder.add(
          git.createDirCacheEntry(
            newPath.uri,
            permission.map(bits => FileMode.fromBits(bits)).getOrElse(FileMode.REGULAR_FILE),
            inserter.insert(Constants.OBJ_BLOB, content)
          )
        )
        builder.finish()
    }
  }

  private def errorFunc(form: Form[NewItem], accountName: String, repositoryName: String)(
    implicit req: RepositoryRequest[AnyContent]
  ): Future[Result] = {
    val rev      = form.data.getOrElse("rev", RzRepository.defaultBranch)
    val path     = form.data.getOrElse("path", ".")
    val isFolder = form.data.get("isFolder")

    val redirect = isFolder match {
      case Some(_) => Redirect(routes.FileEditController.addNewFolderPage(accountName, repositoryName, rev, path))
      case _       => Redirect(routes.FileEditController.addNewItemPage(accountName, repositoryName, rev, path))
    }
    Future(redirect.flashing("error" -> Messages("repository.addNewItem.error.namereq")))
  }

  private def commitNewFile(fName: RzPathUrl, newItem: NewItem)(req: RepositoryRequest[AnyContent]): Future[_] =
    Future {
      git.commitFiles(req.repository, newItem.rev, newItem.path, "Added file", req.account) {
        case (git_, headTip, builder, inserter) =>
          git.processTree(git_, headTip) { (path, tree) =>
            if (!fName.uri.contains(path)) {
              builder.add(
                git.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId)
              )
            }
          }
          val emptyArray = Array.empty[Byte]
          builder.add(
            git.createDirCacheEntry(
              fName.uri,
              FileMode.REGULAR_FILE,
              inserter.insert(Constants.OBJ_BLOB, emptyArray)
            )
          )
          builder.finish()
      }
    }
}
