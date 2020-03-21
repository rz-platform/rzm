package controllers

import java.io.File
import java.nio.file.{Files, Path}
import java.util.Calendar

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink}
import akka.util.ByteString
import git.GitRepository
import javax.inject.Inject
import models._
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.lib.{Constants, FileMode}
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.MessagesApi
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart.FileInfo
import services.path.PathService._
import views._

import scala.concurrent.{ExecutionContext, Future}

class RepositoryController @Inject() (
  gitEntitiesRepository: GitEntitiesRepository,
  accountRepository: AccountRepository,
  userAction: UserInfoAction,
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
    mapping("name" -> nonEmptyText, "description" -> optional(text))(RepositoryData.apply)(RepositoryData.unapply)
  )

  val addCollaboratorForm: Form[NewCollaboratorData] = Form(
    mapping("emailOrLogin" -> nonEmptyText, "accessLevel" -> nonEmptyText)(NewCollaboratorData.apply)(
      NewCollaboratorData.unapply
    )
  )

  val excludedSymbolsForFileName = List('/', ':', '#')

  val addNewItemToRepForm: Form[NewItem] = Form(
    mapping("name" -> nonEmptyText)(NewItem.apply)(NewItem.unapply)
      .verifying("",
                 fields =>
                   fields match {
                     case data =>
                       !excludedSymbolsForFileName.exists(
                         data.name contains _
                       )
                   })
  )

  val editorForm: Form[EditedItem] = Form(
    mapping(
      "content" -> nonEmptyText,
      "message" -> nonEmptyText,
      "newFileName" -> optional(text),
      "oldFileName" -> nonEmptyText
    )(EditedItem.apply)(EditedItem.unapply)
  )

  trait RepositoryAccessException
  class NoRepo extends Exception("Repository does not exist") with RepositoryAccessException
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
              Left(NotFound((new NoCollaborator).getMessage))
            }
          })
          .recover { case e: Exception with RepositoryAccessException => Left(NotFound(e.getMessage)) }
      }
    }

  /**
   * Display list of repositories.
   */
  def list = userAction.async { implicit request =>
    gitEntitiesRepository.listRepositories(request.account.id).map { repositories =>
      Ok(html.listRepositories(repositories))
    }
  }

  def saveRepository = userAction.async { implicit request =>
    createRepositoryForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(html.createRepository(formWithErrors))),
      repository => {
        gitEntitiesRepository.getByAuthorAndName(request.account.userName, repository.name).flatMap {
          case None =>
            val now = Calendar.getInstance().getTime
            val repo = Repository(0, repository.name, true, repository.description.getOrElse(""), "master", now, now)
            gitEntitiesRepository.insertRepository(repo).flatMap { repositoryId: Option[Long] =>
              gitEntitiesRepository
                .createCollaborator(repositoryId.get, request.account.id, 0)
                .map { _ =>
                  val git =
                    new GitRepository(request.account, repository.name, gitHome)
                  git.create()
                  Redirect(routes.RepositoryController.list)
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

  def createRepository = userAction { implicit request =>
    Ok(html.createRepository(createRepositoryForm))
  }

  def view(accountName: String, repositoryName: String, path: String = ".") =
    userAction.andThen(repositoryActionOn(accountName, repositoryName)) { implicit request =>
      val git = new GitRepository(request.repositoryWithOwner.owner, repositoryName, gitHome)
      val gitData = git
        .fileList(request.repositoryWithOwner.repository, path = decodeNameFromUrl(path))
        .getOrElse(RepositoryGitData(List(), None))
      Ok(html.viewRepository(addNewItemToRepForm, gitData, path, buildTreeFromPath(path)))
    }

  def blob(accountName: String, repositoryName: String, rev: String, path: String) =
    userAction.andThen(repositoryActionOn(accountName, repositoryName)) { implicit request =>
      val git = new GitRepository(request.repositoryWithOwner.owner, repositoryName, gitHome)
      val blobInfo = git.blobFile(decodeNameFromUrl(path), rev).get
      Ok(html.viewBlob(blobInfo, path, buildTreeFromPath(path, isFile = true)))
    }

  def editFilePage(accountName: String, repositoryName: String, path: String) =
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.canEdit)) { implicit request =>
      val rev = "master" // TODO: Replace with rev
      val git = new GitRepository(request.repositoryWithOwner.owner, repositoryName, gitHome)
      val blobInfo = git.blobFile(decodeNameFromUrl(path), rev).get
      Ok(html.editFile(editorForm, blobInfo, decodeNameFromUrl(path), buildTreeFromPath(path, isFile = true)))
    }

  def edit(accountName: String, repositoryName: String, path: String) =
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.canEdit)) { implicit request =>
      val rev = "master" // TODO: Replace with rev
      val gitRepository =
        new GitRepository(request.repositoryWithOwner.owner, repositoryName, gitHome)
      val blobInfo = gitRepository.blobFile(decodeNameFromUrl(path), rev).get

      editorForm.bindFromRequest.fold(
        formWithErrors => Future(BadRequest(html.editFile(formWithErrors, blobInfo, path, buildTreeFromPath(path)))),
        editedFile => {
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
      )
      Redirect(routes.RepositoryController.blob(accountName, repositoryName, "master", path))
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
      val path: Path = Files.createTempFile("multipartBody", "tempFile")
      val fileSink: Sink[ByteString, Future[IOResult]] = FileIO.toPath(path)
      val accumulator: Accumulator[ByteString, IOResult] = Accumulator(fileSink)
      accumulator.map {
        case IOResult(count, status) =>
          FilePart(partName, filename, contentType, path.toFile)
      }
  }



  def addNewItem(accountName: String, repositoryName: String, path: String, isFolder: Boolean) =
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
  def upload(accountName: String, repositoryName: String) =
    userAction(parse.multipartFormData(handleFilePartAsFile))
      .andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.canEdit)) { implicit request =>
        val path = "" // TODO
        val files = request.body.files.map(filePart => {
          CommitFile(
            filePart.filename,
            name = if (path.length == 0) filePart.filename else s"${path}/${filePart.filename}",
            filePart.ref
          )
        })

        val gitRepository =
          new GitRepository(request.repositoryWithOwner.owner, repositoryName, gitHome)

        // TODO: replace with rev
        gitRepository.commitFiles("master", ".", "Add files via upload", request.account) {
          case (git, headTip, builder, inserter) =>
            gitRepository.processTree(git, headTip) { (path, tree) =>
              if (!files.exists(_.name.contains(path))) {
                builder.add(
                  gitRepository.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId)
                )
              }
            }

            files.foreach { file =>
              val bytes = FileUtils.readFileToByteArray(file.file)
              builder.add(
                gitRepository
                  .createDirCacheEntry(file.name, FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, bytes))
              )
              builder.finish()
            }
        }
        Redirect(routes.RepositoryController.view(accountName, repositoryName, path))
          .flashing("success" -> s"Uploaded $files.length files")
      }

  def uploadPage(accountName: String, repositoryName: String) =
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.canEdit)) { implicit request =>
      Ok(html.uploadFile())
    }

  def addCollaboratorPage(accountName: String, repositoryName: String) =
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.owner)).async { implicit request =>
      gitEntitiesRepository.getCollaborators(request.repositoryWithOwner.repository).map { collaborators =>
        Ok(html.addCollaborator(addCollaboratorForm, collaborators))
      }
    }

  def addCollaborator(accountName: String, repositoryName: String) =
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.owner)).async { implicit request =>
      val redirect = Redirect(
        routes.RepositoryController.addCollaborator(request.repositoryWithOwner.owner.userName,
                                                    request.repositoryWithOwner.repository.name)
      )

      val formValidatedFunction = { data: NewCollaboratorData =>
        accountRepository
          .findByLoginOrEmail(data.emailOrLogin)
          .flatMap {
            case Some(futureCollaborator) =>
              gitEntitiesRepository
                .isUserCollaborator(request.repositoryWithOwner.repository, futureCollaborator.id)
                .map {
                  case None =>
                    val accessLevel =
                      if (AccessLevel.map.contains(data.accessLevel)) AccessLevel.map(data.accessLevel)
                      else AccessLevel.canView

                    gitEntitiesRepository.createCollaborator(
                      request.repositoryWithOwner.repository.id,
                      futureCollaborator.id,
                      accessLevel
                    )
                  case Some(_) => ()
                }
              Future.successful { redirect }
            case None =>
              Future.successful { redirect.flashing("error" -> s"No such user") }
          }
      }

      addCollaboratorForm.bindFromRequest.fold(
        formWithErrors =>
          gitEntitiesRepository.getCollaborators(request.repositoryWithOwner.repository).map { collaborators =>
            BadRequest(html.addCollaborator(formWithErrors, collaborators))
          },
        formValidatedFunction
      )
    }

  def downloadRepositoryArchive(accountName: String, repositoryName: String, revision: String = "master") = {
    userAction.andThen(repositoryActionOn(accountName, repositoryName, AccessLevel.canView)) { implicit request =>
      val gitRepository =
        new GitRepository(request.repositoryWithOwner.owner, repositoryName, gitHome)

      Ok.sendFile(
        gitRepository.createArchive("", revision),
        inline = false,
        fileName = _ => repositoryName + "-" + revision + ".zip"
      )
    }
  }
}
