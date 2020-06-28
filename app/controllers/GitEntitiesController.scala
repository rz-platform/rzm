package controllers
import java.io.File
import java.nio.file.{ Files, Path }

import actions.AuthenticatedRequest
import akka.stream.IOResult
import akka.stream.scaladsl.{ FileIO, Sink, StreamConverters }
import akka.util.ByteString
import javax.inject.Inject
import models._
import org.eclipse.jgit.lib.{ Constants, FileMode }
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.data.validation.{ Constraint, Invalid, Valid }
import play.api.http.HttpEntity
import play.api.i18n.{ Messages, MessagesApi }
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart.FileInfo
import repositories.{ AccountRepository, GitEntitiesRepository, GitRepository }
import views._

import scala.concurrent.{ ExecutionContext, Future }

class GitEntitiesController @Inject() (
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
      "name" -> nonEmptyText(minLength = 1, maxLength = 36)
        .verifying(pattern(RepositoryNameRegex.toRegex)),
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

  val excludedSymbolsForFileName: List[Char] = List('/', ':', '#')

  val checkForExcludedSymbols: Constraint[String] = Constraint[String] { itemName: String =>
    if (!excludedSymbolsForFileName.exists(itemName contains _))
      Valid
    else
      Invalid("")
  }

  val addNewItemToRepForm: Form[NewItem] = Form(
    mapping("name" -> nonEmptyText.verifying(checkForExcludedSymbols), "rev" -> nonEmptyText)(NewItem.apply)(
      NewItem.unapply
    )
  )

  protected def createBadResult(msg: String, statusCode: Int = BAD_REQUEST): RequestHeader => Future[Result] = {
    request => errorHandler.onClientError(request, statusCode, msg)
  }

  val editorForm: Form[EditedItem] = Form(
    mapping(
      "content"  -> nonEmptyText,
      "rev"      -> nonEmptyText,
      "path"     -> nonEmptyText,
      "fileName" -> nonEmptyText.verifying(checkForExcludedSymbols)
    )(EditedItem.apply)(EditedItem.unapply)
  )

  def getOrElse[T](ifNot: Exception with RepositoryAccessException)(what: => Future[Option[T]]): Future[T] =
    what.map(_.getOrElse(throw ifNot))

  def repositoryActionOn(
    username: String,
    repositoryName: String,
    minimumAccessLevel: AccessLevel = ViewAccess
  ): ActionRefiner[UserRequest, RepositoryRequest] =
    new ActionRefiner[UserRequest, RepositoryRequest] {
      def executionContext: ExecutionContext = ec

      def refine[A](
        request: UserRequest[A]
      ): Future[Either[Result, RepositoryRequest[A]]] = {
        val items = for {
          repository <- getOrElse(new RepositoryAccessException.AccessDenied)(
                         gitEntitiesRepository.getByAuthorAndName(username, repositoryName)
                       )
          collaborator <- gitEntitiesRepository.isUserCollaborator(repository, request.account.id)
        } yield (repository, collaborator)

        items.map { data =>
          val (repository, collaborator) = data

          val accessLevel: Int = collaborator match {
            case None if repository.owner.id == request.account.id => OwnerAccess.role
            case Some(accessLevel)                                 => accessLevel
            case None                                              => throw new RepositoryAccessException.AccessDenied()
          }

          if (accessLevel <= minimumAccessLevel.role) {
            Right(new RepositoryRequest[A](request, repository, request.account, accessLevel, messagesApi))
          } else {
            Left(NotFound("Access denied"))
          }
        }.recover { case ex: Exception with RepositoryAccessException => Left(BadRequest(ex.getMessage)) }
      }
    }

  /**
   * Display list of repositories.
   */
  def list: Action[AnyContent] = authenticatedAction.async { implicit request =>
    gitEntitiesRepository.listRepositories(request.account.id).map { repositories =>
      Ok(html.git.listRepositories(repositories))
    }
  }

  def saveRepository: Action[AnyContent] = authenticatedAction.async { implicit request =>
    createRepositoryForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(html.git.createRepository(formWithErrors))),
      repository =>
        gitEntitiesRepository.getByAuthorAndName(request.account.userName, repository.name).flatMap {
          case None =>
            gitEntitiesRepository.insertRepository(request.account.id, repository).map { _ =>
              val git = new GitRepository(request.account, repository.name, gitHome)
              git.create()
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

  def createRepository: Action[AnyContent] = authenticatedAction { implicit request =>
    Ok(html.git.createRepository(createRepositoryForm))
  }

  def view(accountName: String, repositoryName: String, path: String = ".", rev: String = ""): Action[AnyContent] =
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName)) { implicit request =>
      val git      = new GitRepository(request.repository.owner, repositoryName, gitHome)
      val fileTree = git.fileTree(request.repository, rev)
      Redirect(
        routes.GitEntitiesController
          .blob(accountName, repositoryName, rev, fileTree.getCommonRoot.files.head.pathWithoutRoot)
      )
    }

  def blob(accountName: String, repositoryName: String, rev: String, path: String): Action[AnyContent] =
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName)).async { implicit request =>
      val git      = new GitRepository(request.repository.owner, repositoryName, gitHome)
      val blobInfo = git.blobFile(DecodedPath(path).toString, rev)

      val fileTree = git.fileTree(request.repository, rev)
      blobInfo match {
        case Some(blob) =>
          Future.successful {
            Ok(
              html.git.viewBlob(
                fillEditForm(blob, rev, DecodedPath(path).toString),
                blob,
                path,
                rev,
                Breadcrumbs(path, isFile = true),
                fileTree
              )
            )
          }
        case None => errorHandler.onClientError(request, NOT_FOUND, Messages("error.notfound"))
      }
    }

  def raw(accountName: String, repositoryName: String, rev: String, path: String): Action[AnyContent] =
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName)).async { implicit request =>
      val git = new GitRepository(request.repository.owner, repositoryName, gitHome)
      val raw = git.getRawFile("master", DecodedPath(path).toString)

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

  private def getFileName(path: String): String =
    new File(path).getName

  private def fillEditForm(blob: Blob, rev: String, path: String)(
    implicit req: RepositoryRequest[AnyContent]
  ): Form[EditedItem] =
    editorForm.fill(
      EditedItem(
        blob.content.content.getOrElse(""),
        rev,
        DecodedPath(path).toString,
        getFileName(path)
      )
    )

  def edit(accountName: String, repositoryName: String): Action[AnyContent] =
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName, EditAccess)).async { implicit request =>
      val gitRepository = new GitRepository(request.repository.owner, repositoryName, gitHome)

      val editFile = { editedFile: EditedItem =>
        val oldPath = DecodedPath(editedFile.path).toString
        val newPath = DecodedPath(
          DecodedPath(editedFile.path).pathWithoutFilename,
          editedFile.fileName,
          isFolder = false
        ).toString

        val content = if (editedFile.content.nonEmpty) editedFile.content.getBytes() else Array.emptyByteArray

        gitRepository
          .commitFiles(
            editedFile.rev,
            DecodedPath(editedFile.path).pathWithoutFilename,
            Messages("repository.viewFile.commitMessage", DecodedPath(editedFile.path).nameWithoutPath),
            request.account
          ) {
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
                  permission.map(bits => FileMode.fromBits(bits)).getOrElse(FileMode.REGULAR_FILE),
                  inserter.insert(Constants.OBJ_BLOB, content)
                )
              )
              builder.finish()
          }
        Future(
          Redirect(
            routes.GitEntitiesController.blob(accountName, repositoryName, "master", EncodedPath.fromString(newPath))
          )
        )
      }

      editorForm.bindFromRequest.fold(
        formWithErrors => {
          val rev  = formWithErrors.data.getOrElse("rev", request.repository.defaultBranch)
          val path = formWithErrors.data.get("path")
          path match {
            case Some(path) =>
              val fileTree = gitRepository.fileTree(request.repository, rev)
              val blob     = gitRepository.blobFile(DecodedPath(path).toString, rev)
              blob match {
                case Some(blob) =>
                  Future(
                    BadRequest(
                      html.git
                        .viewBlob(formWithErrors, blob, EncodedPath.fromString(path), rev, Breadcrumbs(path), fileTree)
                    )
                  )
                case None => errorHandler.onClientError(request, NOT_FOUND, Messages("error.notfound"))
              }
            case None => Future(Redirect(routes.GitEntitiesController.view(accountName, repositoryName, ".", rev)))
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
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName, EditAccess)) { implicit request =>
      addNewItemToRepForm.bindFromRequest.fold(
        formWithErrors =>
          Redirect(
            routes.GitEntitiesController.view(
              accountName,
              repositoryName,
              path,
              formWithErrors.data.getOrElse("rev", request.repository.defaultBranch)
            )
          ).flashing("error" -> Messages("repository.addNewItem.error.namereq")),
        newItem => {
          val gitRepository =
            new GitRepository(request.repository.owner, repositoryName, gitHome)

          val fName = DecodedPath(path, newItem.name, isFolder).toString
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

          Redirect(routes.GitEntitiesController.view(accountName, repositoryName, path, newItem.rev))
            .flashing("success" -> Messages("repository.addNewItem.success"))
        }
      )
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
            val gitRepository = new GitRepository(req.repository.owner, repositoryName, gitHome)

            val files: Seq[CommitFile] = req.body.files.map { filePart =>
              val filename = filePart.filename
              val filePath = DecodedPath(data.path, filename, isFolder = false).toString
              CommitFile(filename, name = filePath, filePart.ref)
            }
            gitRepository.commitUploadedFiles(
              files,
              req.account,
              if (!rev.isEmpty) rev else req.repository.defaultBranch,
              data.path,
              Messages("repository.upload.message", files.length)
            )
            Redirect(
              routes.GitEntitiesController.view(accountName, repositoryName, EncodedPath.fromString(data.path), rev)
            ).flashing("success" -> Messages("repository.upload.success"))
          }
        )
      }

  def uploadPage(accountName: String, repositoryName: String, rev: String, path: String): Action[AnyContent] =
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName, EditAccess)) { implicit request =>
      Ok(html.git.uploadFile(uploadFileForm, rev, DecodedPath(path).toString))
    }

  def collaboratorsPage(accountName: String, repositoryName: String): Action[AnyContent] =
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName, OwnerAccess)).async {
      implicit request =>
        gitEntitiesRepository.getCollaborators(request.repository).map { collaborators: Seq[Collaborator] =>
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
          accountRepository.getByLoginOrEmail(data.email).flatMap {
            case Some(collaborator) =>
              gitEntitiesRepository
                .isUserCollaborator(req.repository, collaborator.id)
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
    revision: String = "master"
  ): Action[AnyContent] =
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName, ViewAccess)) { implicit request =>
      val gitRepository = new GitRepository(request.repository.owner, repositoryName, gitHome)

      Ok.sendFile(
        gitRepository.createArchive("", revision),
        inline = false,
        fileName = _ => Some(repositoryName + "-" + revision + ".zip")
      )
    }

  def commitLog(accountName: String, repositoryName: String, rev: String, page: Int): Action[AnyContent] =
    authenticatedAction.andThen(repositoryActionOn(accountName, repositoryName, ViewAccess)).async { implicit request =>
      val gitRepository = new GitRepository(request.repository.owner, repositoryName, gitHome)

      val commitLog = gitRepository.getCommitsLog(rev, page, 30)
      commitLog match {
        case Right((logs, hasNext)) => Future(Ok(html.git.commitLog(logs, rev, hasNext, page)))
        case Left(_)                => errorHandler.onClientError(request, NOT_FOUND, Messages("error.notfound"))
      }
    }
}
