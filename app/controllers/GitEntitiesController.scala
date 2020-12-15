package controllers
import actions.AuthenticatedRequest
import akka.stream.IOResult
import akka.stream.scaladsl.{ FileIO, Sink, StreamConverters }
import akka.util.ByteString
import models._
import org.eclipse.jgit.lib.{ Constants, FileMode }
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.data.validation.{ Constraint, Invalid, Valid, ValidationError }
import play.api.http.HttpEntity
import play.api.i18n.{ Messages, MessagesApi }
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart.FileInfo
import repositories.{ AccountRepository, GitEntitiesRepository, GitRepository }
import views._

import java.io.File
import java.nio.file.{ Files, Path }
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class GitEntitiesController @Inject() (
  git: GitRepository,
  gitEntitiesRepository: GitEntitiesRepository,
  accountRepository: AccountRepository,
  authenticatedAction: AuthenticatedRequest,
  errorHandler: ErrorHandler,
  config: Configuration,
  messagesApi: MessagesApi,
  cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  private val logger = play.api.Logger(this.getClass)

  private val gitHome = config.get[String]("play.server.git.path")

  type FilePartHandler[A] = FileInfo => Accumulator[ByteString, FilePart[A]]

  val createRepositoryForm: Form[RepositoryData] = Form(
    mapping(
      "name"        -> nonEmptyText(minLength = 1, maxLength = 36).verifying(pattern(RepositoryNameRegex.toRegex)),
      "description" -> optional(text(maxLength = 255))
    )(RepositoryData.apply)(RepositoryData.unapply)
  )

  val addCollaboratorForm: Form[NewCollaboratorData] = Form(
    mapping("emailOrLogin" -> nonEmptyText, "accessLevel" -> nonEmptyText)(NewCollaboratorData.apply)(
      NewCollaboratorData.unapply
    )
  )

  val removeCollaboratorForm: Form[RemoveCollaboratorData] = Form(
    mapping("email" -> nonEmptyText)(RemoveCollaboratorData.apply)(RemoveCollaboratorData.unapply)
  )

  val uploadFileForm: Form[UploadFileForm] = Form(
    mapping("path" -> text)(UploadFileForm.apply)(UploadFileForm.unapply)
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
      "content"  -> text,
      "rev"      -> nonEmptyText,
      "path"     -> nonEmptyText.verifying(checkPathForExcludedSymbols),
      "fileName" -> nonEmptyText.verifying(checkNameForExcludedSymbols)
    )(EditedItem.apply)(EditedItem.unapply)
  )

  def repositoryActionOn(
    username: String,
    repoName: String,
    minAccess: AccessLevel
  ): ActionRefiner[AccountRequest, RepositoryRequest] =
    new ActionRefiner[AccountRequest, RepositoryRequest] {
      def executionContext: ExecutionContext = ec

      def refine[A](
        request: AccountRequest[A]
      ): Future[Either[Result, RepositoryRequest[A]]] = {
        val items = for {
          repository: Option[RzRepository] <- gitEntitiesRepository.getByOwnerAndName(username, repoName)
          collaborator: Option[Int]        <- gitEntitiesRepository.isAccountCollaborator(repository, request.account.id)
        } yield (repository, collaborator)
        items.map { data =>
          val (repository, collaborator) = data
          repository match {
            case Some(repo) =>
              AccessLevel.fromAccount(collaborator, repo.owner.id, request.account.id) match {
                case Some(access) if access.role <= minAccess.role =>
                  Right(new RepositoryRequest[A](request, repo, request.account, access, messagesApi))
                case _ => Left(errorHandler.clientError(request, msg = request.messages("error.accessdenied")))
              }
            case _ => Left(errorHandler.clientError(request, msg = request.messages("error.notfound")))
          }
        }
      }
    }

  def createRepository: Action[AnyContent] = authenticatedAction.async { implicit req =>
    Future(Ok(html.git.createRepository(createRepositoryForm)))
  }

  private def clearRepositoryData(data: Option[Map[String, Seq[String]]]): Map[String, Seq[String]] = {
    val form: Map[String, Seq[String]] = data.getOrElse(collection.immutable.Map[String, Seq[String]]())
    form.map {
      case (key, values) if key == "name"        => (key, values.map(_.trim.toLowerCase()))
      case (key, values) if key == "description" => (key, values.map(_.trim))
      case (key, values)                         => (key, values)
    }
  }

  def saveRepository: Action[AnyContent] = authenticatedAction.async { implicit req =>
    val cleanData = clearRepositoryData(req.body.asFormUrlEncoded)
    createRepositoryForm
      .bindFromRequest(cleanData)
      .fold(
        formWithErrors => Future(BadRequest(html.git.createRepository(formWithErrors))),
        repository =>
          gitEntitiesRepository.getByOwnerAndName(req.account.userName, repository.name).flatMap {
            case None =>
              gitEntitiesRepository.insertRepository(req.account.id, repository).map { _ =>
                git.create(RzRepository(0, req.account, repository.name, RzRepository.defaultBranchName, None))
                Redirect(routes.GitEntitiesController.list())
                  .flashing("success" -> Messages("repository.create.flash.success"))
              }
            case Some(_) =>
              val formBuiltFromRequest = createRepositoryForm.bindFromRequest
              val newForm = createRepositoryForm.bindFromRequest.copy(
                errors = formBuiltFromRequest.errors ++ Seq(
                  FormError("name", Messages("repository.create.error.alreadyexists"))
                )
              )
              Future(BadRequest(html.git.createRepository(newForm)))
          }
      )
  }

  /**
   * Display list of repositories.
   */
  def list: Action[AnyContent] = authenticatedAction.async { implicit req =>
    gitEntitiesRepository.listRepositories(req.account.id).map { repositories =>
      Ok(html.git.listRepositories(repositories))
    }
  }

  def raw(accountName: String, repositoryName: String, rev: String, path: String): Action[AnyContent] =
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName, ViewAccess)).async { implicit req =>
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
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName, ViewAccess)) { implicit req =>
      req.repository.mainFile match {
        case Some(mainFile) => // TODO: check that main file exists
          Redirect(
            routes.GitEntitiesController.blob(accountName, repositoryName, rev, EncodedPath.fromString(mainFile))
          )
        case _ =>
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
    }

  def blob(accountName: String, repositoryName: String, rev: String, path: String): Action[AnyContent] =
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName, ViewAccess)).async { implicit req =>
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
            ).withHeaders(("Turbolinks-Location" -> req.uri))
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
      editedFile.fileName,
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
        routes.GitEntitiesController.blob(accountName, repositoryName, editedFile.rev, EncodedPath.fromString(newPath))
      )
    )
  }

  def edit(accountName: String, repositoryName: String): Action[AnyContent] =
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName, EditAccess)).async { implicit req =>
      editorForm.bindFromRequest.fold(
        formWithErrors => {
          val rev  = formWithErrors.data.getOrElse("rev", req.repository.defaultBranch)
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
            case None => Future(Redirect(routes.GitEntitiesController.emptyTree(accountName, repositoryName, rev)))
          }
        },
        (edited: EditedItem) => editFile(edited, accountName, repositoryName)(req)
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

  def addNewItem(accountName: String, repositoryName: String): Action[AnyContent] =
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName, EditAccess)) { implicit req =>
      val cleanData = cleanItemData(req.body.asFormUrlEncoded)
      addNewItemForm
        .bindFromRequest(cleanData)
        .fold(
          formWithErrors =>
            Redirect(
              routes.GitEntitiesController.emptyTree(
                accountName,
                repositoryName,
                formWithErrors.data.getOrElse("rev", req.repository.defaultBranch)
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
              routes.GitEntitiesController.blob(accountName, repositoryName, newItem.rev, EncodedPath.fromString(fName))
            )
          }
        )
    }

  def uploadPage(accountName: String, repositoryName: String, rev: String, path: String): Action[AnyContent] =
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName, EditAccess)).async { implicit request =>
      Future(Ok(html.git.uploadFile(uploadFileForm, rev, DecodedPath(path).toString)))
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
    authenticatedAction(parse.multipartFormData(handleFilePartAsFile))
      .andThen(repositoryActionOn(accountName, repositoryName, EditAccess)) { implicit req =>
        uploadFileForm.bindFromRequest.fold(
          formWithErrors =>
            BadRequest(
              html.git.uploadFile(
                formWithErrors,
                formWithErrors.data.getOrElse("rev", req.repository.defaultBranch),
                formWithErrors.data.getOrElse("path", ".")
              )
            ),
          (data: UploadFileForm) => {
            val files: Seq[CommitFile] = req.body.files.map { filePart =>
              val filename = filePart.filename
              val filePath = DecodedPath(data.path, filename, isFolder = false).toString
              CommitFile(filename, name = filePath, filePart.ref)
            }
            git.commitUploadedFiles(
              req.repository,
              files,
              req.account,
              if (!rev.isEmpty) rev else req.repository.defaultBranch,
              data.path,
              Messages("repository.upload.message", files.length)
            )
            Redirect(
              routes.GitEntitiesController.emptyTree(accountName, repositoryName, rev)
            ).flashing("success" -> Messages("repository.upload.success"))
          }
        )
      }

  def collaboratorsPage(accountName: String, repositoryName: String): Action[AnyContent] =
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName, OwnerAccess)).async { implicit req =>
      gitEntitiesRepository.getCollaborators(req.repository).map { collaborators: Seq[Collaborator] =>
        Ok(html.git.collaborators(addCollaboratorForm, collaborators))
      }
    }

  private def collaboratorPageRedirect(req: RepositoryRequest[AnyContent]): Result =
    Redirect(routes.GitEntitiesController.collaboratorsPage(req.repository.owner.userName, req.repository.name))

  def addCollaborator(accountName: String, repositoryName: String): Action[AnyContent] =
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName, OwnerAccess)).async { implicit req =>
      addCollaboratorForm.bindFromRequest.fold(
        formWithErrors =>
          gitEntitiesRepository.getCollaborators(req.repository).map { collaborators =>
            BadRequest(html.git.collaborators(formWithErrors, collaborators))
          },
        (data: NewCollaboratorData) =>
          accountRepository.getByUsernameOrEmail(data.emailOrLogin).flatMap {
            case Some(futureCollaborator) =>
              gitEntitiesRepository
                .isAccountCollaborator(Some(req.repository), futureCollaborator.id)
                .flatMap {
                  case None =>
                    gitEntitiesRepository
                      .createCollaborator(
                        req.repository.id,
                        futureCollaborator.id,
                        AccessLevel.fromString(data.accessLevel).getOrElse(ViewAccess).role
                      )
                      .flatMap(_ => Future(collaboratorPageRedirect(req)))
                  case Some(_) =>
                    Future(
                      collaboratorPageRedirect(req)
                        .flashing("error" -> Messages("repository.collaborator.error.alreadycollab"))
                    )
                }
            case None =>
              Future(
                collaboratorPageRedirect(req)
                  .flashing("error" -> Messages("repository.collaborator.error.nosuchuser"))
              )
          }
      )
    }

  def removeCollaborator(accountName: String, repositoryName: String): Action[AnyContent] =
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName, OwnerAccess)).async { implicit req =>
      removeCollaboratorForm.bindFromRequest.fold(
        _ =>
          gitEntitiesRepository.getCollaborators(req.repository).map { collaborators =>
            BadRequest(html.git.collaborators(addCollaboratorForm, collaborators))
          },
        data =>
          accountRepository.getByUsernameOrEmail(data.email).flatMap {
            case Some(collaborator) =>
              gitEntitiesRepository
                .isAccountCollaborator(Some(req.repository), collaborator.id)
                .flatMap {
                  case Some(_) =>
                    gitEntitiesRepository
                      .removeCollaborator(req.repository.id, collaborator.id)
                      .flatMap(_ => Future(collaboratorPageRedirect(req)))
                  case None => Future(collaboratorPageRedirect(req))
                }
            case None =>
              Future(
                collaboratorPageRedirect(req)
                  .flashing("error" -> Messages("repository.collaborator.error.nosuchuser"))
              )
          }
      )
    }

  def downloadRepositoryArchive(
    accountName: String,
    repositoryName: String,
    rev: String
  ): Action[AnyContent] =
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName, ViewAccess)) { implicit req =>
      Ok.sendFile(
        git.createArchive(req.repository, "", rev),
        inline = false,
        fileName = _ => Some(repositoryName + "-" + rev + ".zip")
      )
    }

  def commitLog(accountName: String, repositoryName: String, rev: String, page: Int): Action[AnyContent] =
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName, ViewAccess)).async { implicit req =>
      val commitLog = git.getCommitsLog(req.repository, rev, page, 30)
      commitLog match {
        case Right((logs, hasNext)) => Future(Ok(html.git.commitLog(logs, rev, hasNext, page)))
        case Left(_)                => errorHandler.onClientError(req, msg = Messages("error.notfound"))
      }
    }
}
