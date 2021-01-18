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
import play.api.i18n.{ Messages, MessagesApi }
import play.api.mvc._
import repositories._
import views._

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class FileTreeController @Inject() (
  git: GitRepository,
  gitEntitiesRepository: RzGitRepository,
  authenticatedAction: AuthenticatedAction,
  errorHandler: ErrorHandler,
  messagesApi: MessagesApi,
  cc: MessagesControllerComponents,
  repositoryAction: RepositoryAction
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  private val logger = play.api.Logger(this.getClass)

  val createRepositoryForm: Form[RepositoryData] = Form(
    mapping(
      "name"        -> nonEmptyText(minLength = 1, maxLength = 36).verifying(pattern(RepositoryNameRegex.toRegex)),
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
    authenticatedAction.andThen(repositoryAction.on(accountName, repositoryName, ViewAccess)).async { implicit req =>
      val raw = git.getRawFile(req.repository, rev, DecodedPath(path).toString)

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
    authenticatedAction.andThen(repositoryAction.on(accountName, repositoryName, ViewAccess)) { implicit req =>
      // TODO
      val fileTree = git.fileTree(req.repository, rev)
      Ok(
        html.git.viewFile(
          editorForm,
          EmptyBlob,
          "",
          rev,
          EmptyBreadcrumbs(req.repository.name),
          fileTree,
          addNewItemForm.fill(NewItem("", rev, "", isFolder = false))
        )
      )
    }

  def blob(accountName: String, repositoryName: String, rev: String, path: String): Action[AnyContent] =
    authenticatedAction.andThen(repositoryAction.on(accountName, repositoryName, ViewAccess)).async { implicit req =>
      val blobInfo = git.blobFile(req.repository, DecodedPath(path).toString, rev)
      val fileTree = git.fileTree(req.repository, rev)

      blobInfo match {
        case Some(blob) =>
          Future.successful {
            Ok(
              html.git.viewFile(
                editorForm.fill(
                  EditedItem(
                    blob.content.content.getOrElse(""),
                    rev,
                    DecodedPath(path).toString,
                    DecodedPath(path).nameWithoutPath
                  )
                ),
                blob,
                DecodedPath(path).toString,
                rev,
                Breadcrumbs(path, isFile = true),
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
    val oldPath = DecodedPath(editedFile.path).toString
    val newPath = DecodedPath(
      DecodedPath(editedFile.path).pathWithoutFilename,
      editedFile.name,
      isFolder = false
    ).toString

    val content = if (editedFile.content.nonEmpty) editedFile.content.getBytes() else Array.emptyByteArray

    git
      .commitFiles(
        req.repository,
        editedFile.rev,
        DecodedPath(editedFile.path).pathWithoutFilename,
        req.messages("repository.viewFile.commitMessage", DecodedPath(editedFile.path).nameWithoutPath),
        req.account
      ) {
        case (git_, headTip, builder, inserter) =>
          val permission = git
            .processTree(git_, headTip) { (path, tree) =>
              // Add all entries except the editing file
              if (!newPath.contains(path) && !oldPath.contains(path)) {
                builder.add(git.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId))
              }
              // Retrieve permission if file exists to keep it
              oldPath.collect { case x if x.toString == path => tree.getEntryFileMode.getBits }
            }
            .flatten
            .headOption

          builder.add(
            git.createDirCacheEntry(
              newPath,
              permission.map(bits => FileMode.fromBits(bits)).getOrElse(FileMode.REGULAR_FILE),
              inserter.insert(Constants.OBJ_BLOB, content)
            )
          )
          builder.finish()
      }
    Future(
      Redirect(
        routes.FileTreeController.blob(accountName, repositoryName, editedFile.rev, EncodedPath.fromString(newPath))
      )
    )
  }

  private def cleanItemData(data: Option[Map[String, Seq[String]]]): Map[String, Seq[String]] = {
    val form: Map[String, Seq[String]] = data.getOrElse(collection.immutable.Map[String, Seq[String]]())
    form.map {
      case (key, values) if key == "name" => (key, values.map(DecodedPath(_).toString.trim))
      case (key, values) if key == "path" => (key, values.map(DecodedPath(_).toString.trim))
      case (key, values)                  => (key, values)
    }
  }

  def edit(accountName: String, repositoryName: String): Action[AnyContent] =
    authenticatedAction.andThen(repositoryAction.on(accountName, repositoryName, EditAccess)).async { implicit req =>
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
                val blob     = git.blobFile(req.repository, DecodedPath(path).toString, rev)
                blob match {
                  case Some(blob) =>
                    Future(
                      BadRequest(
                        html.git
                          .viewFile(
                            formWithErrors,
                            blob,
                            EncodedPath.fromString(path),
                            rev,
                            Breadcrumbs(path),
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
    authenticatedAction.andThen(repositoryAction.on(accountName, repositoryName, EditAccess)) { implicit req =>
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
            val fName = DecodedPath(newItem.path, newItem.name, newItem.isFolder).toString
            git.commitFiles(req.repository, newItem.rev, newItem.path, "Added file", req.account) {
              case (git_, headTip, builder, inserter) =>
                git.processTree(git_, headTip) { (path, tree) =>
                  if (!fName.contains(path)) {
                    builder.add(
                      git.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId)
                    )
                  }
                }
                val emptyArray = Array.empty[Byte]
                builder.add(
                  git.createDirCacheEntry(
                    fName,
                    FileMode.REGULAR_FILE,
                    inserter.insert(Constants.OBJ_BLOB, emptyArray)
                  )
                )
                builder.finish()
            }
            Redirect(
              routes.FileTreeController.blob(accountName, repositoryName, newItem.rev, EncodedPath.fromString(fName))
            )
          }
        )
    }
}
