package controllers
import actions.{ AuthenticatedAction, RepositoryAction }
import akka.stream.scaladsl.StreamConverters
import models.{ RepositoryRequest, _ }
import org.eclipse.jgit.lib.{ Constants, FileMode }
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.data.validation.{ Constraint, Invalid, Valid, ValidationError }
import play.api.http.HttpEntity
import play.api.i18n.Messages
import play.api.mvc._
import repositories._
import views._

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class FileTreeController @Inject() (
  git: GitRepository,
  errorHandler: ErrorHandler,
  metaGitRepository: RzMetaGitRepository,
  authAction: AuthenticatedAction,
  repoAction: RepositoryAction,
  cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  private val logger = play.api.Logger(this.getClass)

  val createRepositoryForm: Form[RepositoryData] = Form(
    mapping(
      "name"        -> nonEmptyText(minLength = 1, maxLength = 36).verifying(pattern(RzRegex.onlyAlphabet)),
      "description" -> optional(text(maxLength = 255))
    )(RepositoryData.apply)(RepositoryData.unapply)
  )

  val checkNameForExcludedSymbols: Constraint[String] = Constraint[String] { itemName: String =>
    if (!ForbiddenSymbols.isNameValid(itemName)) Valid
    else Invalid(Seq(ValidationError("repository.edit.invalid.name")))
  }

  val checkPathForExcludedSymbols: Constraint[String] = Constraint[String] { itemName: String =>
    if (!ForbiddenSymbols.isPathValid(itemName)) Valid
    else Invalid(Seq(ValidationError("repository.edit.invalid.name")))
  }

  val addNewItemForm: Form[NewItem] = Form(
    mapping(
      "name"     -> nonEmptyText.verifying(checkNameForExcludedSymbols),
      "rev"      -> nonEmptyText,
      "path"     -> nonEmptyText.verifying(checkPathForExcludedSymbols),
      "isFolder" -> boolean
    )(NewItem.apply)(NewItem.unapply)
  )

  val editorForm: Form[EditedItem] = Form(
    mapping(
      "content" -> text,
      "rev"     -> nonEmptyText,
      "path"    -> nonEmptyText.verifying(checkPathForExcludedSymbols),
      "name"    -> nonEmptyText.verifying(checkNameForExcludedSymbols)
    )(EditedItem.apply)(EditedItem.unapply)
  )

  def raw(accountName: String, repositoryName: String, rev: String, path: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, ViewAccess)).async { implicit req =>
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
    authAction.andThen(repoAction.on(accountName, repositoryName, ViewAccess)) { implicit req =>
      val fileTree = git.fileTree(req.repository, rev)
      req.repository.lastOpenedFile match {
        case Some(path) =>
          Redirect(
            routes.FileTreeController.blob(accountName, repositoryName, rev, path)
          )
        case _ =>
          Ok(
            html.git.fileTree(
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
    authAction.andThen(repoAction.on(accountName, repositoryName, ViewAccess)).async { implicit req =>
      val rzPath   = RzPathUrl.make(path)
      val blobInfo = git.blobFile(req.repository, rzPath.uri, rev)
      val fileTree = git.fileTree(req.repository, rev)
      blobInfo match {
        case Some(blob) =>
          metaGitRepository.setRzRepoLastFile(req.repository, path)
          Future.successful {
            Ok(
              html.git.fileTree(
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

  private def editFile(
    editedFile: EditedItem,
    accountName: String,
    repositoryName: String
  )(req: RepositoryRequest[AnyContent]): Future[Result] = {
    val oldPath = RzPathUrl.make(editedFile.path)
    val newPath = RzPathUrl.make(oldPath.pathWithoutFilename, editedFile.name, isFolder = false)

    val content = if (editedFile.content.nonEmpty) editedFile.content.getBytes() else Array.emptyByteArray

    git.commitFiles(
      req.repository,
      editedFile.rev,
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
    Future(
      Redirect(
        routes.FileTreeController.blob(accountName, repositoryName, editedFile.rev, newPath.encoded)
      )
    )
  }

  private def cleanItemData(data: Option[Map[String, Seq[String]]]): Map[String, Seq[String]] = {
    val form: Map[String, Seq[String]] = data.getOrElse(collection.immutable.Map[String, Seq[String]]())
    form.map {
      case (key, values) if key == "name" => (key, values.map(RzPathUrl.make(_).uri.trim))
      case (key, values) if key == "path" => (key, values.map(RzPathUrl.make(_).uri.trim))
      case (key, values)                  => (key, values)
    }
  }

  def edit(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, EditAccess)).async { implicit req =>
      val cleanData = cleanItemData(req.body.asFormUrlEncoded)
      editorForm
        .bindFromRequest(cleanData)
        .fold(
          formWithErrors => {
            val rev  = formWithErrors.data.getOrElse("rev", RzRepository.defaultBranch)
            val path = formWithErrors.data.get("path")
            path match {
              case Some(path) =>
                val fileTree = git.fileTree(req.repository, rev)
                val rzpath   = RzPathUrl.make(path)
                val blob     = git.blobFile(req.repository, rzpath.uri, rev)
                blob match {
                  case Some(blob) =>
                    Future(
                      BadRequest(
                        html.git
                          .fileTree(
                            formWithErrors,
                            blob,
                            rzpath.encoded,
                            rev,
                            new FilePath(path),
                            fileTree,
                            addNewItemForm.fill(NewItem("", rev, "", isFolder = false))
                          )
                      )
                    )
                  case None => errorHandler.onClientError(req, msg = Messages("error.notfound"))
                }
              case None => Future(Redirect(routes.FileTreeController.emptyTree(accountName, repositoryName, rev)))
            }
          },
          (edited: EditedItem) => editFile(edited, accountName, repositoryName)(req)
        )
    }

  def addNewItem(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, EditAccess)) { implicit req =>
      val cleanData = cleanItemData(req.body.asFormUrlEncoded)
      addNewItemForm
        .bindFromRequest(cleanData)
        .fold(
          formWithErrors =>
            Redirect(
              routes.FileTreeController.emptyTree(
                accountName,
                repositoryName,
                formWithErrors.data.getOrElse("rev", RzRepository.defaultBranch)
              )
            ).flashing("error" -> Messages("repository.addNewItem.error.namereq")),
          (newItem: NewItem) => {
            val fName = RzPathUrl.make(newItem.path, newItem.name, newItem.isFolder)

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
            Redirect(
              routes.FileTreeController.blob(accountName, repositoryName, newItem.rev, fName.encoded)
            )
          }
        )
    }
}
