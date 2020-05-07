package controllers
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Calendar

import akka.stream.IOResult
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import git.GitRepository
import javax.inject.Inject
import models._
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.data.validation.Constraint
import play.api.data.validation.Invalid
import play.api.data.validation.Valid
import play.api.http.HttpEntity
import play.api.i18n.Messages
import play.api.i18n.MessagesApi
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart.FileInfo
import services.path.PathService._
import views._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class RepositoryController @Inject() (
    gitEntitiesRepository: GitEntitiesRepository,
    accountRepository: AccountRepository,
    userAction: UserInfoAction,
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
      "name" -> nonEmptyText(minLength = 1, maxLength = 36)
        .verifying(pattern("^[A-Za-z\\d_\\-]+$".r)),
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
    mapping("path" -> text, "message" -> nonEmptyText)(UploadFileForm.apply)(UploadFileForm.unapply)
  )

  val excludedSymbolsForFileName: List[Char] = List('/', ':', '#')

  val checkForExcludedSymbols: Constraint[String] = Constraint[String] { itemName: String =>
    if (!excludedSymbolsForFileName.exists(itemName contains _))
      Valid
    else
      Invalid("")
  }

  val addNewItemToRepForm: Form[NewItem] = Form(
    mapping("name" -> nonEmptyText.verifying(checkForExcludedSymbols))(NewItem.apply)(NewItem.unapply)
  )

  protected def createBadResult(msg: String, statusCode: Int = BAD_REQUEST): RequestHeader => Future[Result] = {
    request =>
      errorHandler.onClientError(request, statusCode, msg)
  }

  val editorForm: Form[EditedItem] = Form(
    mapping(
      "content"  -> nonEmptyText,
      "message"  -> nonEmptyText,
      "path"     -> nonEmptyText,
      "fileName" -> nonEmptyText.verifying(checkForExcludedSymbols)
    )(EditedItem.apply)(EditedItem.unapply)
  )

  trait RepositoryAccessException
  class NoRepo         extends Exception("Repository does not exist") with RepositoryAccessException
  class NoCollaborator extends Exception("Access denied") with RepositoryAccessException

  def getOrElse[T](ifNot: Exception with RepositoryAccessException)(what: => Future[Option[T]]): Future[T] =
    what.map(_.getOrElse(throw ifNot))

  def repositoryActionOn(
      username: String,
      repositoryName: String,
      minimumAccessLevel: Int = AccessLevel.canView
  ): ActionRefiner[UserRequest, RepositoryRequest] =
    new ActionRefiner[UserRequest, RepositoryRequest] {
      def executionContext: ExecutionContext = ec

      def refine[A](
          request: UserRequest[A]
      ): Future[Either[Result, controllers.RepositoryRequest[A]]] = {
        val items = for {
          repository <- getOrElse(new NoRepo)(
            gitEntitiesRepository.getByAuthorAndName(username, repositoryName)
          )
          collaborator <- gitEntitiesRepository.isUserCollaborator(repository, request.account.id)
        } yield (repository, collaborator)

        items
          .map(data => {
            val accessLevel = data._2 match {
              case None if data._1.owner.id == request.account.id => AccessLevel.owner
              case Some(accessLevel)                              => accessLevel
              case None                                           => throw new NoCollaborator()
            }

            if (accessLevel <= minimumAccessLevel) {
              Right(new RepositoryRequest[A](request, data._1, request.account, accessLevel, messagesApi))
            } else {
              Left(NotFound("Access denied"))
            }
          })
          .recover { case ex: Exception with RepositoryAccessException => Left(BadRequest(ex.getMessage)) }
      }
    }

  /**
   * Display list of repositories.
   */
  def list: Action[AnyContent] = userAction.async { implicit request =>
    gitEntitiesRepository.listRepositories(request.account.id).map { repositories =>
      Ok(html.listRepositories(repositories))
    }
  }

  def saveRepository: Action[AnyContent] = userAction.async { implicit request =>
    createRepositoryForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(html.createRepository(formWithErrors))),
      repository => {
        gitEntitiesRepository.getByAuthorAndName(request.account.userName, repository.name).flatMap {
          case None =>
            gitEntitiesRepository.insertRepository(request.account.id, repository).flatMap {
              repositoryId: Option[Long] =>
                gitEntitiesRepository
                  .createCollaborator(repositoryId.get, request.account.id, 0)
                  .map { _ =>
                    val git = new GitRepository(request.account, repository.name, gitHome)
                    git.create()
                    Redirect(routes.RepositoryController.list())
                      .flashing("success" -> Messages("repository.create.flash.success"))
                  }
            }
          case Some(_) =>
            val formBuiltFromRequest = createRepositoryForm.bindFromRequest
            val newForm = createRepositoryForm.bindFromRequest.copy(
              errors = formBuiltFromRequest.errors ++ Seq(
                FormError("name", Messages("repository.create.error.alreadyexists"))
              )
            )
            Future(BadRequest(html.createRepository(newForm)))
        }
      }
    )
  }

  def createRepository: Action[AnyContent] = userAction { implicit request =>
    Ok(html.createRepository(createRepositoryForm))
  }

  def view(accountName: String, repositoryName: String, path: String = "."): Action[AnyContent] =
    userAction.andThen(repositoryActionOn(accountName, repositoryName)) { implicit request =>
      val git = new GitRepository(request.repository.owner, repositoryName, gitHome)
      val gitData = git
        .fileList(request.repository, path = decodeNameFromUrl(path))
        .getOrElse(RepositoryGitData(List(), None))
      Ok(html.viewRepository(addNewItemToRepForm, gitData, path, buildTreeFromPath(path)))
    }

  def blob(accountName: String, repositoryName: String, rev: String, path: String): Action[AnyContent] =
    userAction.andThen(repositoryActionOn(accountName, repositoryName)).async { implicit request =>
      val git      = new GitRepository(request.repository.owner, repositoryName, gitHome)
      val blobInfo = git.blobFile(decodeNameFromUrl(path), rev)
      blobInfo match {
        case Some(blob) =>
          Future.successful {
            Ok(html.viewBlob(blob, path, buildTreeFromPath(path, isFile = true)))
          }
        case None => errorHandler.onClientError(request, NOT_FOUND, Messages("error.notfound"))
      }
    }

  def raw(accountName: String, repositoryName: String, rev: String, path: String): Action[AnyContent] =
    userAction.andThen(repositoryActionOn(accountName, repositoryName)).async { implicit request =>
      val git = new GitRepository(request.repository.owner, repositoryName, gitHome)
      val raw = git.getRawFile("master", decodeNameFromUrl(path))

      raw match {
        case Some(rawFile) =>
          val stream = StreamConverters.fromInputStream(() => rawFile.inputStream)
          Future.successful {
            Result(
              header = ResponseHeader(200, Map.empty),
              body = HttpEntity.Streamed(stream, Some(rawFile.contentLength.toLong), Some(rawFile.contentType))
            )
          }
        case None => errorHandler.onClientError(request, NOT_FOUND, Messages("error.notfound"))
      }
    }

  private def getFileName(path: String): String = {
    new File(path).getName
  }

  private def fillEditForm(blob: Blob, path: String)(implicit req: RepositoryRequest[AnyContent]): Form[EditedItem] = {
    editorForm.fill(
      EditedItem(
        blob.content.content.getOrElse(""),
        Messages("repository.edit.commitmessage"),
        decodeNameFromUrl(path),
        getFileName(path)
      )
    )
  }

  def editFilePage(accountName: String, repositoryName: String, path: String): Action[AnyContent] =
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.canEdit)).async { implicit request =>
      val rev      = "master" // TODO: Replace with rev
      val git      = new GitRepository(request.repository.owner, repositoryName, gitHome)
      val blobInfo = git.blobFile(decodeNameFromUrl(path), rev)
      blobInfo match {
        case Some(blob) =>
          Future.successful {
            Ok(
              html.editFile(
                fillEditForm(blob, decodeNameFromUrl(path)),
                blob,
                decodeNameFromUrl(path),
                buildTreeFromPath(path, isFile = true)
              )
            )
          }
        case None => errorHandler.onClientError(request, NOT_FOUND, Messages("error.notfound"))
      }
    }

  def edit(accountName: String, repositoryName: String): Action[AnyContent] =
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.canEdit)).async { implicit request =>
      val gitRepository = new GitRepository(request.repository.owner, repositoryName, gitHome)
      val rev = "master"

      val editFile = { editedFile: EditedItem =>
        val oldPath       = decodeNameFromUrl(editedFile.path)
        val newPath       = buildFilePath(getPathWithoutFilename(editedFile.path), editedFile.fileName, isFolder = false)

        val content  = if (editedFile.content.nonEmpty) editedFile.content.getBytes() else Array.emptyByteArray

        gitRepository.commitFiles(rev, getPathWithoutFilename(oldPath), editedFile.message, request.account) {
          case (git, headTip, builder, inserter) =>
            val permission = gitRepository
              .processTree(git, headTip) { (path, tree) =>
                // Add all entries except the editing file
                if (!newPath.contains(path) && !oldPath.contains(path)) {
                  builder.add(gitRepository.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId))
                }
                // Retrieve permission if file exists to keep it
                oldPath.collect { case x if x.toString == path => tree.getEntryFileMode.getBits }
              }
              .flatten
              .headOption

            builder.add(
              gitRepository.createDirCacheEntry(
                newPath,
                permission
                  .map { bits =>
                    FileMode.fromBits(bits)
                  }
                  .getOrElse(FileMode.REGULAR_FILE),
                inserter.insert(Constants.OBJ_BLOB, content)
              )
            )
            builder.finish()
        }
        Future(Redirect(routes.RepositoryController.blob(accountName, repositoryName, "master", newPath)))
      }

      editorForm.bindFromRequest.fold(
        formWithErrors => {
          val path = formWithErrors.data.get("path")
          path match {
            case Some(path) =>
              logger.info(path)
              val blob = gitRepository.blobFile(decodeNameFromUrl(path), rev)
              blob match {
                case Some(blob) => Future(BadRequest(html.editFile(formWithErrors, blob, path, buildTreeFromPath(path))))
                case None => errorHandler.onClientError(request, NOT_FOUND, Messages("error.notfound"))
              }
            case None => Future(Redirect(routes.RepositoryController.view(accountName, repositoryName, ".")))
          }
        },
        editFile
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
        case IOResult(count, status) =>
          FilePart(partName, filename, contentType, path.toFile)
      }
  }

  def addNewItem(accountName: String, repositoryName: String, path: String, isFolder: Boolean): Action[AnyContent] =
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.canEdit)) { implicit request =>
      addNewItemToRepForm.bindFromRequest.fold(
        _ =>
          Redirect(routes.RepositoryController.view(accountName, repositoryName, path))
            .flashing("error" -> Messages("repository.addNewItem.error.namereq")),
        newItem => {
          val gitRepository =
            new GitRepository(request.repository.owner, repositoryName, gitHome)

          val fName = buildFilePath(path, newItem.name, isFolder)
          // TODO: replace with rev
          gitRepository
            .commitFiles("master", path, "Added file", request.account) {
              case (git, headTip, builder, inserter) =>
                gitRepository.processTree(git, headTip) { (path, tree) =>
                  if (!fName.contains(path)) {
                    builder.add(
                      gitRepository.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId)
                    )
                  }
                }
                val emptyArray = Array.empty[Byte]
                builder.add(
                  gitRepository.createDirCacheEntry(
                    fName,
                    FileMode.REGULAR_FILE,
                    inserter.insert(Constants.OBJ_BLOB, emptyArray)
                  )
                )
                builder.finish()
            }

          Redirect(routes.RepositoryController.view(accountName, repositoryName, path))
            .flashing("success" -> Messages("repository.addNewItem.success"))
        }
      )
    }

  /**
   * Uploads a multipart file as a POST request.
   *
   */
  def upload(accountName: String, repositoryName: String): Action[MultipartFormData[File]] =
    userAction(parse.multipartFormData(handleFilePartAsFile))
      .andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.canEdit)) { implicit req =>
        uploadFileForm.bindFromRequest.fold(
          formWithErrors => BadRequest(html.uploadFile(formWithErrors, "")),
          (data: UploadFileForm) => {
            val gitRepository = new GitRepository(req.repository.owner, repositoryName, gitHome)

            val files: Seq[CommitFile] = req.body.files.map(filePart => {
              // only get the last part of the filename
              // otherwise someone can send a path like ../../home/foo/bar.txt to write to other files on the system
              val filename = Paths.get(filePart.filename).getFileName.toString
              val filePath = buildFilePath(data.path, filename, isFolder = false)
              CommitFile(filename, name = filePath, filePart.ref)
            })

            gitRepository.commitUploadedFiles(files, req.account, "master", data.path, data.message)
            Redirect(routes.RepositoryController.view(accountName, repositoryName, data.path))
              .flashing("success" -> Messages("repository.upload.success"))
          }
        )
      }

  def uploadPage(accountName: String, repositoryName: String, path: String): Action[AnyContent] =
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.canEdit)) { implicit request =>
      Ok(html.uploadFile(uploadFileForm, decodeNameFromUrl(path)))
    }

  def addCollaboratorPage(accountName: String, repositoryName: String): Action[AnyContent] =
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.owner)).async { implicit request =>
      gitEntitiesRepository.getCollaborators(request.repository).map { collaborators =>
        Ok(html.addCollaborator(addCollaboratorForm, collaborators))
      }
    }

  private def getCollaboratorPageRedirect(req: RepositoryRequest[AnyContent]): Result = {
    Redirect(
      routes.RepositoryController
        .addCollaboratorPage(req.repository.owner.userName, req.repository.name)
    )
  }

  def addCollaboratorAction(accountName: String, repositoryName: String): Action[AnyContent] =
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.owner)).async { implicit req =>
      addCollaboratorForm.bindFromRequest.fold(
        formWithErrors =>
          gitEntitiesRepository.getCollaborators(req.repository).map { collaborators =>
            BadRequest(html.addCollaborator(formWithErrors, collaborators))
          },
        (data: NewCollaboratorData) =>
          accountRepository.getByLoginOrEmail(data.emailOrLogin).flatMap {
            case Some(futureCollaborator) =>
              gitEntitiesRepository
                .isUserCollaborator(req.repository, futureCollaborator.id)
                .flatMap {
                  case None =>
                    gitEntitiesRepository
                      .createCollaborator(
                        req.repository.id,
                        futureCollaborator.id,
                        AccessLevel.fromString(data.accessLevel)
                      )
                      .flatMap { _ =>
                        Future(getCollaboratorPageRedirect(req))
                      }
                  case Some(_) =>
                    Future(
                      getCollaboratorPageRedirect(req)
                        .flashing("error" -> Messages("repository.collaborator.error.alreadycollab"))
                    )
                }
            case None =>
              Future(
                getCollaboratorPageRedirect(req)
                  .flashing("error" -> Messages("repository.collaborator.error.nosuchuser"))
              )
          }
      )
    }

  def removeCollaboratorAction(accountName: String, repositoryName: String): Action[AnyContent] =
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.owner)).async { implicit req =>
      removeCollaboratorForm.bindFromRequest.fold(
        _ =>
          gitEntitiesRepository.getCollaborators(req.repository).map { collaborators =>
            BadRequest(html.addCollaborator(addCollaboratorForm, collaborators))
          },
        data =>
          accountRepository.getByLoginOrEmail(data.email).flatMap {
            case Some(collaborator) =>
              gitEntitiesRepository
                .isUserCollaborator(req.repository, collaborator.id)
                .flatMap {
                  case Some(_) =>
                    gitEntitiesRepository
                      .removeCollaborator(req.repository.id, collaborator.id)
                      .flatMap(_ => Future(getCollaboratorPageRedirect(req)))
                  case None => Future(getCollaboratorPageRedirect(req))
                }
            case None =>
              Future(
                getCollaboratorPageRedirect(req)
                  .flashing("error" -> Messages("repository.collaborator.error.nosuchuser"))
              )
          }
      )
    }

  def downloadRepositoryArchive(
      accountName: String,
      repositoryName: String,
      revision: String = "master"
  ): Action[AnyContent] = {
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.canView)) { implicit request =>
      val gitRepository = new GitRepository(request.repository.owner, repositoryName, gitHome)

      Ok.sendFile(
        gitRepository.createArchive("", revision),
        inline = false,
        fileName = _ => Some(repositoryName + "-" + revision + ".zip")
      )
    }
  }

  def commitLog(accountName: String, repositoryName: String, rev: String, page: Int): Action[AnyContent] ={
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.canView)).async { implicit request =>
      val gitRepository = new GitRepository(request.repository.owner, repositoryName, gitHome)

      val commitLog = gitRepository.getCommitsLog(rev, page, 30)
      commitLog match {
        case Right((logs, hasNext)) => Future(Ok(html.commitLog(logs, rev, hasNext, page)))
        case Left(_) => errorHandler.onClientError(request, NOT_FOUND, Messages("error.notfound"))
      }
    }
  }
}
