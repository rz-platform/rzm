import java.sql.{Connection, Statement}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import controllers.{RepositoryController, routes}
import git.GitRepository
import models.{Account, Repository, RepositoryGitData}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.Configuration
import play.api.db.DBApi
import play.api.db.evolutions.Evolutions
import play.api.mvc.{Flash, Result}
import play.api.test.CSRFTokenHelper._
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Future

class FunctionalRepoTest
    extends PlaySpec
    with BeforeAndAfterAll
    with GuiceOneAppPerSuite
    with Injecting
    with ScalaFutures {
  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  implicit val sys: ActorSystem = ActorSystem("RepositoryTest")
  implicit val mat: ActorMaterializer = ActorMaterializer()

  def getRandomString: String = {
    java.util.UUID.randomUUID.toString
  }

  lazy val databaseApi: DBApi = app.injector.instanceOf[DBApi]
  val config = app.injector.instanceOf[Configuration]
  val controller = app.injector.instanceOf[RepositoryController]

  override def beforeAll(): Unit = {
    Evolutions.applyEvolutions(databaseApi.database("default"))
  }

  override def afterAll(): Unit = {
    Evolutions.cleanupEvolutions(databaseApi.database("default"))
  }

  def getDbConnection: Connection = {
    databaseApi.database("default").getConnection()
  }

  def createUser(): Account = {
    val userName = getRandomString
    val userPreparedStatement = getDbConnection
      .prepareStatement(
        "insert into account (userName, mailAddress, password, isAdmin, isRemoved) " +
          s"values ('$userName', '$getRandomString', '$getRandomString', false, false)",
        Statement.RETURN_GENERATED_KEYS
      )
    userPreparedStatement.executeUpdate()
    val rs = userPreparedStatement.getGeneratedKeys
    rs.next()
    Account(rs.getInt(1), userName, null, null, null, false, null)
  }

  def createRepository(controller: RepositoryController, name: String, owner: Account): Result = {
    val request = addCSRFToken(
      FakeRequest(routes.RepositoryController.saveRepository())
        .withFormUrlEncodedBody("name" -> name, "description" -> getRandomString)
        .withSession(("user_id", owner.id.toString))
    )

    await(controller.saveRepository().apply(request))
  }

  def createNewItem(
    controller: RepositoryController,
    name: String,
    repositoryName: String,
    creator: Account,
    isFolder: Boolean,
    path: String
  ): Result = {
    val newFileRequest = addCSRFToken(
      FakeRequest()
        .withFormUrlEncodedBody("name" -> name)
        .withSession(("user_id", creator.id.toString))
    )

    val result: Future[Result] =
      call(controller.addNewItem(creator.userName, repositoryName, path, isFolder = isFolder), newFileRequest)

    await(result)
  }

  "RepositoryController" must {
    "Create Repository" in {
      val account = createUser()
      val repoId = getRandomString

      val result = createRepository(controller, repoId, account)
      result.header.headers(LOCATION) must equal(routes.RepositoryController.list().toString)

      val isRepoExist = getDbConnection
        .prepareStatement(s"select 1 FROM repository WHERE name='$repoId'")
        .executeQuery()
      isRepoExist.next() must equal(true)
    }

    def listFileInRepo(gitRepository: GitRepository, repository: Repository, path: String): RepositoryGitData = {
      gitRepository
        .fileList(repository, path = path)
        .getOrElse(RepositoryGitData(List(), None))

    }

    "Create file in repository" in {
      val account = createUser()
      val repoId = getRandomString
      val fileId = getRandomString

      createRepository(controller, repoId, account)

      createNewItem(controller, fileId, repoId, account, isFolder = false, ".")

      val git = new GitRepository(account, repoId, config.get[String]("play.server.git.path"))
      val listRootFiles = listFileInRepo(git, Repository(0, repoId, true, null, "master", null, null), ".")

      listRootFiles.files.exists(_.name contains fileId) must equal(true)
    }

    "Create folder in repository" in {
      val account = createUser()
      val repoId = getRandomString
      val folderId = getRandomString

      createRepository(controller, repoId, account)

      createNewItem(controller, folderId, repoId, account, isFolder = true, ".")

      val git = new GitRepository(account, repoId, config.get[String]("play.server.git.path"))
      val repo = Repository(0, repoId, true, null, "master", null, null)
      val rootFileList = listFileInRepo(git, repo, ".")

      rootFileList.files.exists(_.name contains folderId) must equal(true)

      val folderFileList = listFileInRepo(git, repo, folderId)
      folderFileList.files.exists(_.name contains ".gitkeep") must equal(true)
    }

    "Attempt to create file with forbidden symbols" in {
      val account = createUser()

      val repoId = getRandomString
      val fileId = getRandomString + controller.excludedSymbolsForFileName.mkString("-")

      createRepository(controller, repoId, account)

      val result = createNewItem(controller, fileId, repoId, account, isFolder = false, ".")
      result.header.status must equal(303)
      result.newFlash.getOrElse(Flash(Map())).data.contains("error") must equal(true)
    }

    "Attempt to create folder with forbidden symbols" in {
      val account = createUser()

      val repoId = getRandomString
      val folderId = getRandomString + controller.excludedSymbolsForFileName.mkString("-")

      createRepository(controller, repoId, account)

      val result = createNewItem(controller, folderId, repoId, account, isFolder = true, ".")
      result.header.status must equal(303)
      result.newFlash.getOrElse(Flash(Map())).data.contains("error") must equal(true)
    }

    "Create file in subfolder" in {
      val account = createUser()

      val repoId = getRandomString
      val folderId = getRandomString
      val fileInSubfolderId = getRandomString

      createRepository(controller, repoId, account)

      createNewItem(controller, folderId, repoId, account, isFolder = true, ".")
      createNewItem(controller, fileInSubfolderId, repoId, account, isFolder = true, folderId)

      val git = new GitRepository(account, repoId, config.get[String]("play.server.git.path"))
      val repo = Repository(0, repoId, true, null, "master", null, null)
      val folderFileList = listFileInRepo(git, repo, folderId)
      folderFileList.files.exists(_.name contains fileInSubfolderId) must equal(true)
    }

    "Create folder in subfolder" in {
      val account = createUser()

      val repoId = getRandomString
      val folderId = getRandomString
      val folderInSubfolderId = getRandomString

      createRepository(controller, repoId, account)

      createNewItem(controller, folderId, repoId, account, isFolder = true, ".")
      createNewItem(controller, folderInSubfolderId, repoId, account, isFolder = true, folderId)

      val git = new GitRepository(account, repoId, config.get[String]("play.server.git.path"))
      val repo = Repository(0, repoId, true, null, "master", null, null)
      val folderFileList = listFileInRepo(git, repo, folderId)
      folderFileList.files.exists(_.name contains folderInSubfolderId) must equal(true)
    }

    "Create folder with spaces in subfolder" in {
      val account = createUser()

      val repoId = getRandomString
      val folderId = getRandomString + " " + getRandomString
      val folderInSubfolderId = getRandomString + " " + getRandomString

      createRepository(controller, repoId, account)

      createNewItem(controller, folderId, repoId, account, isFolder = true, ".")
      createNewItem(controller, folderInSubfolderId, repoId, account, isFolder = true, folderId)

      val git = new GitRepository(account, repoId, config.get[String]("play.server.git.path"))
      val repo = Repository(0, repoId, true, null, "master", null, null)
      val folderFileList = listFileInRepo(git, repo, folderId)
      folderFileList.files.exists(_.name contains folderInSubfolderId) must equal(true)
    }

    "Create file with spaces in subfolder1" in {
      val account = createUser()

      val repoId = getRandomString
      val folderId = getRandomString + " " + getRandomString
      val fileInSubfolderId = getRandomString + " " + getRandomString

      createRepository(controller, repoId, account)

      createNewItem(controller, folderId, repoId, account, isFolder = true, ".")
      createNewItem(controller, fileInSubfolderId, repoId, account, isFolder = true, folderId)

      val git = new GitRepository(account, repoId, config.get[String]("play.server.git.path"))
      val repo = Repository(0, repoId, true, null, "master", null, null)
      val folderFileList = listFileInRepo(git, repo, folderId)
      folderFileList.files.exists(_.name contains fileInSubfolderId) must equal(true)
    }

    "Create file with spaces in subfolder" in {
      val account = createUser()

      val repoId = getRandomString
      val folderId = getRandomString + " " + getRandomString
      val fileInSubfolderId = getRandomString + " " + getRandomString

      createRepository(controller, repoId, account)

      createNewItem(controller, folderId, repoId, account, isFolder = true, ".")
      createNewItem(controller, fileInSubfolderId, repoId, account, isFolder = true, folderId)

      val git = new GitRepository(account, repoId, config.get[String]("play.server.git.path"))
      val repo = Repository(0, repoId, true, null, "master", null, null)
      val folderFileList = listFileInRepo(git, repo, folderId)
      folderFileList.files.exists(_.name contains fileInSubfolderId) must equal(true)
    }
  }
}
