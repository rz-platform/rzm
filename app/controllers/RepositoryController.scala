package controllers

import java.io.File
import java.nio.file.{Files, Path}
import java.util.Calendar

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink}
import akka.util.ByteString
import javax.inject.Inject
import models._
import play.api.data.Forms._
import play.api.data._
import play.api.mvc._
import views._
import play.api.Configuration
import git.GitRepository
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.lib.{Constants, FileMode}
import play.api.i18n.MessagesApi
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.core.parsers.Multipart.FileInfo

import scala.concurrent.{ExecutionContext, Future}

class RepositoryController @Inject()(repRepository: RepRepository,
                                     accountRepository: AccountRepository,
                                     userAction: UserInfoAction,
                                     config: Configuration,
                                     messagesApi: MessagesApi,
                                     cc: MessagesControllerComponents)(implicit ec: ExecutionContext)
  extends MessagesAbstractController(cc) with play.api.i18n.I18nSupport {

  private val logger = play.api.Logger(this.getClass)

  private val gitHome = config.get[String]("git.path")

  type FilePartHandler[A] = FileInfo => Accumulator[ByteString, FilePart[A]]

  val createRepositoryForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> optional(text)
    )(RepositoryData.apply)(RepositoryData.unapply)
  )

  val addCollaboratorForm = Form(
    mapping(
      "emailOrLogin" -> nonEmptyText,
      "accessLevel" -> nonEmptyText
    )(NewCollaboratorData.apply)(NewCollaboratorData.unapply)
  )

  val addNewItemToRepForm = Form(
    mapping(
      "name" -> nonEmptyText
    )(NewItem.apply)(NewItem.unapply)
  )

  val editorForm = Form(
    mapping(
    "content" -> nonEmptyText,
    "message" -> nonEmptyText,
    "newFileName" -> optional(text),
    "oldFileName" -> nonEmptyText
    )(EditedItem.apply)(EditedItem.unapply)
  )

  def repositoryActionOn(username: String, repositoryName: String) =
    new ActionRefiner[UserRequest, RepositoryRequest] {
      def executionContext: ExecutionContext = ec

      def refine[A](request: UserRequest[A]) = {
        // TODO: acces check
        repRepository.getByAuthorAndName(username, repositoryName).map {
          case Some(repositoryWithAccount) => {
            val account: Account = repositoryWithAccount._2.get
            val repository: Repository = repositoryWithAccount._1
            Right(new RepositoryRequest[A](request, repository, account, request.account, messagesApi))
          }
          case other => Left(NotFound)
        }
      }
    }

  /**
    * Display list of repositories.
    */
  def list = userAction.async { implicit request =>
    repRepository.listRepositories(request.account.id).map { repositories =>
      Ok(html.listRepositories(repositories))
    }
  }

  def saveRepository = userAction.async { implicit request =>
    createRepositoryForm.bindFromRequest.fold(
      formWithErrors => Future(BadRequest(html.createRepository(formWithErrors))),
      repository => {
        val now = Calendar.getInstance().getTime
        val repo = Repository(0, repository.name, true, repository.description, "master", now, now)
        repRepository.insertRepository(repo).map { repositoryId: Option[Long] =>
          repRepository.createCollaborator(repositoryId.get, request.account.id, 0).map {
            collaboratorId =>
              val git = new GitRepository(request.account, repository.name, gitHome)
              git.create()
          }
        }
      })
    Future(Redirect(routes.RepositoryController.list).flashing("success" -> s"Repository created"))
  }

  def createRepository = userAction { implicit request =>
    Ok(html.createRepository(createRepositoryForm))
  }

  def view(accountName: String, repositoryName: String, path: String = ".") =
    userAction.andThen(repositoryActionOn(accountName, repositoryName)) { implicit request =>
      val git = new GitRepository(request.owner, repositoryName, gitHome)
      val gitData = git.fileList(request.repository, path = path).getOrElse(RepositoryGitData(List(), None))
      Ok(html.viewRepository(addNewItemToRepForm, request.owner, request.repository, gitData, path))
    }

  def blob(accountName: String, repositoryName: String, rev: String, path: String) =
    userAction.andThen(repositoryActionOn(accountName, repositoryName)) { implicit request =>
      val git = new GitRepository(request.owner, repositoryName, gitHome)
      val blobInfo = git.blobFile(path, rev).get
      Ok(html.viewBlob(request.owner, repositoryName, blobInfo))
    }


  def editFilePage(accountName: String, repositoryName: String, path: String) =
    userAction.andThen(repositoryActionOn(accountName, repositoryName)) { implicit request =>
      val rev = "master" // TODO: Replace with the default branch
      val git = new GitRepository(request.owner, repositoryName, gitHome)
      val blobInfo = git.blobFile(path, rev).get
      Ok(html.editFile(editorForm, request.owner, repositoryName, blobInfo, path))
  }

  def edit(accountName: String, repositoryName: String, path: String) =
    userAction.andThen(repositoryActionOn(accountName, repositoryName)) { implicit request =>
      val rev = "master" // TODO: Replace with the default branch
      val gitRepository = new GitRepository(request.owner, repositoryName, gitHome)
      val blobInfo = gitRepository.blobFile(path, rev).get

      editorForm.bindFromRequest.fold(
        formWithErrors => Future(BadRequest(html.editFile(formWithErrors, request.owner, repositoryName, blobInfo, path))),
        editedFile => {
          val fName = editedFile.oldFileName
          val content = if (editedFile.content.nonEmpty) { editedFile.content.getBytes() } else { Array.emptyByteArray }
          gitRepository.commitFiles("master", ".", editedFile.message, request.account) {
            case (git, headTip, builder, inserter) =>
              gitRepository.processTree(git, headTip) { (path, tree) =>
                if (!fName.contains(path)) {
                  builder.add(gitRepository.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId))
                }
              }
              val emptyArray =  Array.empty[Byte]
              builder.add(
                gitRepository.createDirCacheEntry(fName, FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, content))
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
          logger.info(s"count = $count, status = $status")
          FilePart(partName, filename, contentType, path.toFile)
      }
  }

  def addNewItem(accountName: String, repositoryName: String, path: String, isFolder: Boolean)
   = userAction(parse.multipartFormData(handleFilePartAsFile)).andThen(repositoryActionOn(accountName, repositoryName)) {
    implicit request =>
      addNewItemToRepForm.bindFromRequest.fold(
        formWithErrors => Redirect(routes.RepositoryController.view(accountName, repositoryName, path))
          .flashing("error" -> s"Name is required"),
        newItem => {
          val gitRepository = new GitRepository(request.owner, repositoryName, gitHome)
          var fName = newItem.name
          if (isFolder) {
            fName += "/.gitkeep"
          }

          gitRepository.commitFiles("master", ".", "Added file", request.account) {
            case (git, headTip, builder, inserter) =>
              gitRepository.processTree(git, headTip) { (path, tree) =>
                if (!fName.contains(path)) {
                  builder.add(gitRepository.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId))
                }
              }
              val emptyArray =  Array.empty[Byte]
              builder.add(
                  gitRepository.createDirCacheEntry(fName, FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, emptyArray))
                )
              builder.finish()
          }

          Redirect(routes.RepositoryController.view(accountName, repositoryName, path))
            .flashing("success" -> s"Item is successfully created")
        })
  }

  /**
    * Uploads a multipart file as a POST request.
    *
    * @return
    */
  def upload(accountName: String, repositoryName: String)
  = userAction(parse.multipartFormData(handleFilePartAsFile)).andThen(repositoryActionOn(accountName, repositoryName)) { implicit request =>
    val path = ""
    val files = request.body.files.map(filePart => {
      CommitFile(filePart.filename, name = if (path.length == 0) filePart.filename else s"${path}/${filePart.filename}", filePart.ref)
    })

    val gitRepository = new GitRepository(request.owner, repositoryName, gitHome)

    // TODO: replace with default branch
    gitRepository.commitFiles("master", ".", "Add files via upload", request.account) {
      case (git, headTip, builder, inserter) =>
        gitRepository.processTree(git, headTip) { (path, tree) =>
          if (!files.exists(_.name.contains(path))) {
            builder.add(gitRepository.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId))
          }
        }

        files.foreach { file =>
          logger.info(s"$file.")
          val bytes =
            FileUtils.readFileToByteArray(file.file)
          builder.add(
            gitRepository.createDirCacheEntry(file.name, FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, bytes))
          )
          builder.finish()
        }
    }
    Redirect(routes.RepositoryController.view(accountName, repositoryName, path))
      .flashing("success" -> s"Uploaded $files.length files")
  }

  def uploadPage(accountName: String, repositoryName: String) = userAction { implicit request =>
    Ok(html.uploadFile(accountName, repositoryName))
  }

  def addCollaboratorPage(accountName: String, repositoryName: String)
  = userAction.andThen(repositoryActionOn(accountName, repositoryName)).async { implicit request =>
    repRepository.getCollaborators(request.repository).map {
      collaborators => Ok(html.addCollaborator(addCollaboratorForm, request.owner, request.repository, collaborators))
    }
  }

  def addCollaborator(accountName: String, repositoryName: String)
  = userAction.andThen(repositoryActionOn(accountName, repositoryName)).async { implicit request =>
    addCollaboratorForm.bindFromRequest.fold(formWithErrors =>
      repRepository.getCollaborators(request.repository).map { collaborators =>
        BadRequest(html.addCollaborator(formWithErrors, request.owner, request.repository, collaborators))
      },
      collaboratorData => {
        accountRepository.findByLoginOrEmail(collaboratorData.emailOrLogin).flatMap {
          case Some(account) =>
            repRepository.isUserCollaborator(request.repository, account.id).map {
              isUserCollaborator: Boolean =>
                if (!isUserCollaborator)
                  repRepository.createCollaborator(request.repository.id, account.id, 1)
            }
            Future.successful {
              Redirect(routes.RepositoryController.addCollaborator(request.owner.userName, request.repository.name))
            }
          case None =>
            Future.successful {
              Redirect(routes.RepositoryController.addCollaborator(request.owner.userName, request.repository.name))
                .flashing("error" -> s"No such user")
            }

        }
      }
    )
  }
}