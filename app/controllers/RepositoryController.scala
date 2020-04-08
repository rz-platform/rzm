package controllers

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Calendar

import akka.stream.IOResult
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import git.GitRepository
import javax.inject.Inject
import models._
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.http.HttpEntity
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
    extends MessagesAbstractController(cc)
    with play.api.i18n.I18nSupport {

  private val logger = play.api.Logger(this.getClass)

  private val gitHome = config.get[String]("play.server.git.path")

  type FilePartHandler[A] = FileInfo => Accumulator[ByteString, FilePart[A]]

  val createRepositoryForm: Form[RepositoryData] = Form(
    mapping("name" -> nonEmptyText(minLength=1, maxLength=25),
      "description" -> optional(text(maxLength = 255)))(RepositoryData.apply)(RepositoryData.unapply)
      .verifying(
        "",
        fields =>
          fields match {
            case data => data.name.matches("^[A-Za-z\\d_-]+$")
          }
      )
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

  val excludedSymbolsForFileName = Array('/', ':', '#')

  val addNewItemToRepForm: Form[NewItem] = Form(
    mapping("name" -> nonEmptyText)(NewItem.apply)(NewItem.unapply)
      .verifying(
        "File name contains forbidden symbols",
        fields =>
          fields match {
            case data => !excludedSymbolsForFileName.exists(data.name contains _)
          }
      )
  )

  protected def createBadResult(msg: String, statusCode: Int = BAD_REQUEST): RequestHeader => Future[Result] = {
    request =>
      errorHandler.onClientError(request, statusCode, msg)
  }

  val editorForm: Form[EditedItem] = Form(
    mapping(
      "content"     -> nonEmptyText,
      "message"     -> nonEmptyText,
      "newFileName" -> optional(text),
      "oldFileName" -> nonEmptyText
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
          repositoryWithOwner <- getOrElse(new NoRepo)(
            gitEntitiesRepository.getByAuthorAndName(username, repositoryName)
          )
          collaborator <- getOrElse(new NoCollaborator)(
            gitEntitiesRepository.isUserCollaborator(repositoryWithOwner.repository, request.account.id)
          )
        } yield (repositoryWithOwner, collaborator)

        items
          .map(data => {
            val accessLevel = data._2

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
            val now = Calendar.getInstance().getTime
            val repo =
              Repository(0, repository.name, isPrivate = true, repository.description.getOrElse(""), "master", now, now)
            gitEntitiesRepository.insertRepository(repo).flatMap { repositoryId: Option[Long] =>
              gitEntitiesRepository
                .createCollaborator(repositoryId.get, request.account.id, 0)
                .map { _ =>
                  val git =
                    new GitRepository(request.account, repository.name, gitHome)
                  git.create()
                  Redirect(routes.RepositoryController.list())
                    .flashing("success" -> s"Repository created")
                }
            }
          case Some(_) =>
            val formBuiltFromRequest = createRepositoryForm.bindFromRequest
            val newForm = createRepositoryForm.bindFromRequest.copy(
              errors = formBuiltFromRequest.errors ++ Seq(FormError("name", "Repository already exists."))
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
      val git = new GitRepository(request.repositoryWithOwner.owner, repositoryName, gitHome)
      val gitData = git
        .fileList(request.repositoryWithOwner.repository, path = decodeNameFromUrl(path))
        .getOrElse(RepositoryGitData(List(), None))
      Ok(html.viewRepository(addNewItemToRepForm, gitData, path, buildTreeFromPath(path)))
    }

  def blob(accountName: String, repositoryName: String, rev: String, path: String): Action[AnyContent] =
    userAction.andThen(repositoryActionOn(accountName, repositoryName)).async { implicit request =>
      val git      = new GitRepository(request.repositoryWithOwner.owner, repositoryName, gitHome)
      val blobInfo = git.blobFile(decodeNameFromUrl(path), rev)
      blobInfo match {
        case Some(blob) =>
          Future.successful {
            Ok(html.viewBlob(blob, path, buildTreeFromPath(path, isFile = true)))
          }
        case None => errorHandler.onClientError(request, 404, "Not Found")
      }
    }

  def raw(accountName: String, repositoryName: String, rev: String, path: String): Action[AnyContent] =
    userAction.andThen(repositoryActionOn(accountName, repositoryName)).async { implicit request =>
      val git = new GitRepository(request.repositoryWithOwner.owner, repositoryName, gitHome)
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
        case None => errorHandler.onClientError(request, 404, "Not Found")
      }

    }

  def editFilePage(accountName: String, repositoryName: String, path: String): Action[AnyContent] =
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.canEdit)).async { implicit request =>
      val rev      = "master" // TODO: Replace with rev
      val git      = new GitRepository(request.repositoryWithOwner.owner, repositoryName, gitHome)
      val blobInfo = git.blobFile(decodeNameFromUrl(path), rev)
      blobInfo match {
        case Some(blob) =>
          Future.successful {
            Ok(html.editFile(editorForm, blob, decodeNameFromUrl(path), buildTreeFromPath(path, isFile = true)))
          }
        case None => errorHandler.onClientError(request, 404, "Not Found")
      }
    }

  def edit(accountName: String, repositoryName: String, path: String): Action[AnyContent] =
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.canEdit)) { implicit request =>
      val rev           = "master" // TODO: Replace with rev
      val gitRepository = new GitRepository(request.repositoryWithOwner.owner, repositoryName, gitHome)
      val blobInfo      = gitRepository.blobFile(decodeNameFromUrl(path), rev)

      val editFile = { editedFile: EditedItem =>
        val fName = editedFile.oldFileName
        val content = if (editedFile.content.nonEmpty) {
          editedFile.content.getBytes()
        } else {
          Array.emptyByteArray
        }
        gitRepository
          .commitFiles(rev, ".", editedFile.message, request.account) {
            case (git, headTip, builder, inserter) =>
              gitRepository.processTree(git, headTip) { (path, tree) =>
                if (!fName.contains(path)) {
                  builder.add(
                    gitRepository.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId)
                  )
                }
              }
              builder.add(
                gitRepository
                  .createDirCacheEntry(fName, FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, content))
              )
              builder.finish()
          }
      }

      blobInfo match {
        case Some(blob) =>
          editorForm.bindFromRequest.fold(
            formWithErrors => Future(BadRequest(html.editFile(formWithErrors, blob, path, buildTreeFromPath(path)))),
            editFile
          )
          Redirect(routes.RepositoryController.blob(accountName, repositoryName, "master", path))
        case None => NotFound
      }
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
            .flashing("error" -> s"Name is required"),
        newItem => {
          val gitRepository =
            new GitRepository(request.repositoryWithOwner.owner, repositoryName, gitHome)

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
            .flashing("success" -> s"Item is successfully created")
        }
      )
    }

  /**
   * Uploads a multipart file as a POST request.
   *
   * @return
   */
  def upload(accountName: String, repositoryName: String): Action[MultipartFormData[File]] =
    userAction(parse.multipartFormData(handleFilePartAsFile))
    .andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.canEdit)) { implicit req =>
        uploadFileForm.bindFromRequest.fold(
          formWithErrors =>    BadRequest(html.uploadFile(formWithErrors, "")),
          (data: UploadFileForm) => {
            val gitRepository = new GitRepository(req.repositoryWithOwner.owner, repositoryName, gitHome)

            val files: Seq[CommitFile] = req.body.files.map(filePart => {
              CommitFile(
                filePart.filename,
                name = if (data.path.trim.nonEmpty) s"${data.path}/${filePart.filename}" else filePart.filename,
                filePart.ref
              )
            })

            gitRepository.commitUploadedFiles(files, req.account, "master", data.path, data.message)
            Redirect(routes.RepositoryController.view(accountName, repositoryName, data.path))
              .flashing("success" -> s"Uploaded $files.length files")
          }
        )
      }

  def uploadPage(accountName: String, repositoryName: String, path: String): Action[AnyContent] =
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.canEdit)) { implicit request =>
      Ok(html.uploadFile(uploadFileForm, path))
    }

  def addCollaboratorPage(accountName: String, repositoryName: String): Action[AnyContent] =
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.owner)).async { implicit request =>
      gitEntitiesRepository.getCollaborators(request.repositoryWithOwner.repository).map { collaborators =>
        Ok(html.addCollaborator(addCollaboratorForm, collaborators))
      }
    }

  private def getCollaboratorPageRedirect(req: RepositoryRequest[AnyContent]): Result = {
    Redirect(
      routes.RepositoryController
        .addCollaboratorPage(req.repositoryWithOwner.owner.userName, req.repositoryWithOwner.repository.name)
    )
  }

  def addCollaboratorAction(accountName: String, repositoryName: String): Action[AnyContent] =
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.owner)).async { implicit req =>
      addCollaboratorForm.bindFromRequest.fold(
        formWithErrors =>
          gitEntitiesRepository.getCollaborators(req.repositoryWithOwner.repository).map { collaborators =>
            BadRequest(html.addCollaborator(formWithErrors, collaborators))
          },
        (data: NewCollaboratorData) =>
          accountRepository.findByLoginOrEmail(data.emailOrLogin).flatMap {
            case Some(futureCollaborator) =>
              gitEntitiesRepository
                .isUserCollaborator(req.repositoryWithOwner.repository, futureCollaborator.id)
                .flatMap {
                  case None =>
                    gitEntitiesRepository
                      .createCollaborator(
                        req.repositoryWithOwner.repository.id,
                        futureCollaborator.id,
                        AccessLevel.fromString(data.accessLevel)
                      )
                      .flatMap { _ =>
                        Future(getCollaboratorPageRedirect(req))
                      }
                  case Some(_) =>
                    Future(
                      getCollaboratorPageRedirect(req)
                        .flashing("error" -> s"User is already a collaborator")
                    )
                }
            case None => Future(getCollaboratorPageRedirect(req).flashing("error" -> s"No such user"))
          }
      )
    }

  def removeCollaboratorAction(accountName: String, repositoryName: String): Action[AnyContent] =
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.owner)).async { implicit req =>
      removeCollaboratorForm.bindFromRequest.fold(
        _ =>
          gitEntitiesRepository.getCollaborators(req.repositoryWithOwner.repository).map { collaborators =>
            BadRequest(html.addCollaborator(addCollaboratorForm, collaborators))
          },
        data =>
          accountRepository.findByLoginOrEmail(data.email).flatMap {
            case Some(collaborator) =>
              gitEntitiesRepository
                .isUserCollaborator(req.repositoryWithOwner.repository, collaborator.id)
                .flatMap {
                  case Some(_) =>
                    gitEntitiesRepository
                      .removeCollaborator(req.repositoryWithOwner.repository.id, collaborator.id)
                      .flatMap(_ => Future(getCollaboratorPageRedirect(req)))
                  case None => Future(getCollaboratorPageRedirect(req))
                }
            case None => Future(getCollaboratorPageRedirect(req).flashing("error" -> s"No such user"))
          }
      )
    }

  def downloadRepositoryArchive(
      accountName: String,
      repositoryName: String,
      revision: String = "master"
  ): Action[AnyContent] = {
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.canView)) { implicit request =>
      val gitRepository =
        new GitRepository(request.repositoryWithOwner.owner, repositoryName, gitHome)

      Ok.sendFile(
        gitRepository.createArchive("", revision),
        inline = false,
        fileName = _ => Some(repositoryName + "-" + revision + ".zip")
      )
    }
  }
}
