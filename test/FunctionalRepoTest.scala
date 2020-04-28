import java.sql.Statement

import akka.actor.ActorSystem
import controllers.AuthController
import controllers.RepositoryController
import controllers.routes
import git.GitRepository
import models._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.PrivateMethodTester
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.Configuration
import play.api.db.DBApi
import play.api.db.Database
import play.api.db.evolutions.Evolutions
import play.api.mvc.Flash
import play.api.mvc.Result
import play.api.test.CSRFTokenHelper._
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Future

class FunctionalRepoTest
    extends PlaySpec
    with BeforeAndAfterAll
    with GuiceOneAppPerSuite
    with Injecting
    with PrivateMethodTester
    with ScalaFutures {
  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  implicit val sys: ActorSystem = ActorSystem("RepositoryTest")

  def getRandomString: String = {
    java.util.UUID.randomUUID.toString
  }

  def databaseApi: DBApi               = app.injector.instanceOf[DBApi]
  def config: Configuration            = app.injector.instanceOf[Configuration]
  def controller: RepositoryController = app.injector.instanceOf[RepositoryController]
  def authController: AuthController   = app.injector.instanceOf[AuthController]

  def accountRepository: AccountRepository         = app.injector.instanceOf[AccountRepository]
  def gitEntitiesRepository: GitEntitiesRepository = app.injector.instanceOf[GitEntitiesRepository]

  val defaultDatabase: Database = databaseApi.database("default")

  override def beforeAll(): Unit = {
    Evolutions.applyEvolutions(databaseApi.database("default"))
  }

  override def afterAll(): Unit = {
    Evolutions.cleanupEvolutions(databaseApi.database("default"))
  }

  def createUser(): Account = {
    val userName = getRandomString
    val request = addCSRFToken(
      FakeRequest(routes.AuthController.saveUser())
        .withFormUrlEncodedBody(
          "userName"    -> userName,
          "fullName"    -> getRandomString,
          "password"    -> getRandomString,
          "mailAddress" -> s"${getRandomString}@razam.dev"
        )
    )
    await(authController.saveUser().apply(request))
    defaultDatabase.withConnection { connection =>
      val rs = connection.prepareStatement(s"select id from account where username='${userName}'").executeQuery()
      rs.next()
      Account(rs.getInt(1), userName, null, hasPicture = false)
    }
  }

  def createRepository(controller: RepositoryController, name: String, owner: Account): Result = {
    val request = addCSRFToken(
      FakeRequest(routes.RepositoryController.saveRepository())
        .withFormUrlEncodedBody("name" -> name, "description" -> getRandomString)
        .withSession(("user_id", owner.id.toString))
    )

    await(controller.saveRepository().apply(request))
  }

  def addCollaborator(
      controller: RepositoryController,
      repositoryName: String,
      owner: Account,
      collaboratorName: String,
      accessLevel: String
  ): Result = {
    val request = addCSRFToken(
      FakeRequest(routes.RepositoryController.addCollaboratorAction(owner.userName, repositoryName))
        .withFormUrlEncodedBody("emailOrLogin" -> collaboratorName, "accessLevel" -> accessLevel)
        .withSession(("user_id", owner.id.toString))
    )

    await(controller.addCollaboratorAction(owner.userName, repositoryName).apply(request))
  }

  def removeCollaborator(
      controller: RepositoryController,
      repositoryName: String,
      owner: Account,
      collaboratorName: String
  ): Result = {
    val request = addCSRFToken(
      FakeRequest(routes.RepositoryController.removeCollaboratorAction(owner.userName, repositoryName))
        .withFormUrlEncodedBody("email" -> collaboratorName)
        .withSession(("user_id", owner.id.toString))
    )

    await(controller.removeCollaboratorAction(owner.userName, repositoryName).apply(request))
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

  def listFileInRepo(gitRepository: GitRepository, repository: Repository, path: String): RepositoryGitData = {
    gitRepository
      .fileList(repository, path = path)
      .getOrElse(RepositoryGitData(List(), None))

  }

  "AuthController" must {
    "Attempt to create user with bad name" in {
      val badUserNames =
        List("&", "!", "%", "киррилица", "with space", "^", "@", "--++", "::", "t)", "exceededlength" * 10)
      badUserNames.map { username =>
        val request = addCSRFToken(
          FakeRequest(routes.AuthController.saveUser())
            .withFormUrlEncodedBody(
              "userName"    -> username,
              "fullName"    -> getRandomString,
              "password"    -> getRandomString,
              "mailAddress" -> s"${getRandomString}@razam.dev"
            )
        )

        val result = await(authController.saveUser().apply(request))
        result.header.status must equal(400)
      }
    }

    "Successful attempt to create user" in {
      val goodUserNames = List(getRandomString, "with_underscore", "with-minus", "MiXeDcAse")
      goodUserNames.map { username =>
        val request = addCSRFToken(
          FakeRequest(routes.AuthController.saveUser())
            .withFormUrlEncodedBody(
              "userName"    -> username,
              "fullName"    -> getRandomString,
              "password"    -> getRandomString,
              "mailAddress" -> s"${getRandomString}@razam.dev"
            )
        )

        val result = await(authController.saveUser().apply(request))
        result.header.status must equal(303)

        defaultDatabase.withConnection { connection =>
          val rs = connection
            .prepareStatement(
              s"select 1 from account where account.username='${username.toLowerCase}'"
            )
            .executeQuery()
          rs.next() must equal(true)
        }
      }
    }
  }

  "RepositoryController" must {
    "Create Repository" in {
      val account  = createUser()
      val repoName = getRandomString

      val result = createRepository(controller, repoName, account)
      result.header.headers(LOCATION) must equal(routes.RepositoryController.list().toString)

      defaultDatabase.withConnection { connection =>
        val isRepoExist = connection
          .prepareStatement(s"select 1 FROM repository WHERE name='$repoName'")
          .executeQuery()
        isRepoExist.next() must equal(true)
      }
    }

    "Create file in repository" in {
      val account  = createUser()
      val repoName = getRandomString
      val fileName = getRandomString

      createRepository(controller, repoName, account)

      createNewItem(controller, fileName, repoName, account, isFolder = false, ".")

      val git           = new GitRepository(account, repoName, config.get[String]("play.server.git.path"))
      val listRootFiles = listFileInRepo(git, Repository(0, account, repoName, "master"), ".")

      listRootFiles.files.exists(_.name contains fileName) must equal(true)
    }

    "Create folder in repository" in {
      val account    = createUser()
      val repoName   = getRandomString
      val folderName = getRandomString

      createRepository(controller, repoName, account)

      createNewItem(controller, folderName, repoName, account, isFolder = true, ".")

      val git          = new GitRepository(account, repoName, config.get[String]("play.server.git.path"))
      val repo         = Repository(0, account, repoName, "master")
      val rootFileList = listFileInRepo(git, repo, ".")

      rootFileList.files.exists(_.name contains folderName) must equal(true)

      val folderFileList = listFileInRepo(git, repo, folderName)
      folderFileList.files.exists(_.name contains ".gitkeep") must equal(true)
    }

    "Attempt to create file with forbidden symbols" in {
      val account = createUser()

      val repoName = getRandomString
      val fileName = getRandomString + controller.excludedSymbolsForFileName.mkString("-")

      createRepository(controller, repoName, account)

      val result = createNewItem(controller, fileName, repoName, account, isFolder = false, ".")
      result.header.status must equal(303)
      result.newFlash.getOrElse(Flash(Map())).data.contains("error") must equal(true)
    }

    "Attempt to create folder with forbidden symbols" in {
      val account = createUser()

      val repoName   = getRandomString
      val folderName = getRandomString + controller.excludedSymbolsForFileName.mkString("-")

      createRepository(controller, repoName, account)

      val result = createNewItem(controller, folderName, repoName, account, isFolder = true, ".")
      result.header.status must equal(303)
      result.newFlash.getOrElse(Flash(Map())).data.contains("error") must equal(true)
    }

    "Create file in subfolder" in {
      val account = createUser()

      val repoName            = getRandomString
      val folderName          = getRandomString
      val fileInSubfolderName = getRandomString

      createRepository(controller, repoName, account)

      createNewItem(controller, folderName, repoName, account, isFolder = true, ".")
      createNewItem(controller, fileInSubfolderName, repoName, account, isFolder = true, folderName)

      val git            = new GitRepository(account, repoName, config.get[String]("play.server.git.path"))
      val repo           = Repository(0, account, repoName, "master")
      val folderFileList = listFileInRepo(git, repo, folderName)
      folderFileList.files.exists(_.name contains fileInSubfolderName) must equal(true)
    }

    "Create folder in subfolder" in {
      val account = createUser()

      val repoName              = getRandomString
      val folderName            = getRandomString
      val folderInSubfolderName = getRandomString

      createRepository(controller, repoName, account)

      createNewItem(controller, folderName, repoName, account, isFolder = true, ".")
      createNewItem(controller, folderInSubfolderName, repoName, account, isFolder = true, folderName)

      val git            = new GitRepository(account, repoName, config.get[String]("play.server.git.path"))
      val repo           = Repository(0, account, repoName, "master")
      val folderFileList = listFileInRepo(git, repo, folderName)
      folderFileList.files.exists(_.name contains folderInSubfolderName) must equal(true)
    }

    "Create folder with spaces in subfolder" in {
      val account = createUser()

      val repoName              = getRandomString
      val folderName            = getRandomString + " " + getRandomString
      val folderInSubfolderName = getRandomString + " " + getRandomString

      createRepository(controller, repoName, account)

      createNewItem(controller, folderName, repoName, account, isFolder = true, ".")
      createNewItem(controller, folderInSubfolderName, repoName, account, isFolder = true, folderName)

      val git            = new GitRepository(account, repoName, config.get[String]("play.server.git.path"))
      val repo           = Repository(0, account, repoName, "master")
      val folderFileList = listFileInRepo(git, repo, folderName)
      folderFileList.files.exists(_.name contains folderInSubfolderName) must equal(true)
    }

    "Create file with spaces in subfolder" in {
      val account = createUser()

      val repoName            = getRandomString
      val folderName          = getRandomString + " " + getRandomString
      val fileInSubfolderName = getRandomString + " " + getRandomString

      createRepository(controller, repoName, account)

      createNewItem(controller, folderName, repoName, account, isFolder = true, ".")
      createNewItem(controller, fileInSubfolderName, repoName, account, isFolder = true, folderName)

      val git            = new GitRepository(account, repoName, config.get[String]("play.server.git.path"))
      val repo           = Repository(0, account, repoName, "master")
      val folderFileList = listFileInRepo(git, repo, folderName)
      folderFileList.files.exists(_.name contains fileInSubfolderName) must equal(true)
    }

    "Add collaborator successfully" in {
      val owner    = createUser()
      val repoName = getRandomString
      createRepository(controller, repoName, owner)
      val collaborator = createUser()
      val result       = addCollaborator(controller, repoName, owner, collaborator.userName, AccessLevel.canEditName)

      result.header.status must equal(303)
      defaultDatabase.withConnection { connection =>
        val collaboratorStatement = connection
          .prepareStatement(
            s"select 1 FROM collaborator WHERE user_id=${collaborator.id}" +
              s"and repository_id=(select id FROM repository WHERE name='$repoName')" +
              s"and role=${AccessLevel.canEdit}"
          )
          .executeQuery()
        collaboratorStatement.next() must equal(true)
      }
    }

    "Add collaborator with wrong data" in {
      val owner    = createUser()
      val repoName = getRandomString
      createRepository(controller, repoName, owner)
      val collaborator = createUser()
      addCollaborator(controller, repoName, owner, "nonexistentusername", AccessLevel.canEditName)

      defaultDatabase.withConnection { connection =>
        val isCollaboratorExist = connection
          .prepareStatement(
            s"select 1 FROM collaborator WHERE user_id=${collaborator.id} and " +
              s"repository_id=(select id FROM repository WHERE name='$repoName')"
          )
          .executeQuery()
        isCollaboratorExist.next() must equal(false)
      }
    }

    "Add collaborator that already exists" in {
      val owner    = createUser()
      val repoName = getRandomString
      createRepository(controller, repoName, owner)
      val collaborator = createUser()
      val result       = addCollaborator(controller, repoName, owner, collaborator.userName, AccessLevel.canEditName)

      result.header.status must equal(303)
      defaultDatabase.withConnection { connection =>
        val collaboratorStatement = connection
          .prepareStatement(
            s"select 1 FROM collaborator WHERE user_id=${collaborator.id}" +
              s"and repository_id=(select id FROM repository WHERE name='$repoName')" +
              s"and role=${AccessLevel.canEdit}"
          )
          .executeQuery()
        collaboratorStatement.next() must equal(true)
      }

      addCollaborator(controller, repoName, owner, collaborator.userName, AccessLevel.canEditName)
      result.header.status must equal(303)
    }

    "Remove collaborator" in {
      val owner    = createUser()
      val repoName = getRandomString
      createRepository(controller, repoName, owner)
      val collaborator = createUser()

      addCollaborator(controller, repoName, owner, collaborator.userName, AccessLevel.canEditName)
      defaultDatabase.withConnection { connection =>
        val collaboratorStatement = connection
          .prepareStatement(
            s"select 1 FROM collaborator WHERE user_id=${collaborator.id}" +
              s"and repository_id=(select id FROM repository WHERE name='$repoName')" +
              s"and role=${AccessLevel.canEdit}"
          )
          .executeQuery()
        collaboratorStatement.next() must equal(true)
      }

      val result = removeCollaborator(controller, repoName, owner, collaborator.userName)

      result.header.status must equal(303)
      defaultDatabase.withConnection { connection =>
        val collaboratorStatement = connection
          .prepareStatement(
            s"select 1 FROM collaborator WHERE user_id=${collaborator.id}" +
              s"and repository_id=(select id FROM repository WHERE name='$repoName')"
          )
          .executeQuery()
        collaboratorStatement.next() must equal(false)
      }
    }

    "Remove collaborator with non-existent userid" in {
      val owner    = createUser()
      val repoName = getRandomString
      createRepository(controller, repoName, owner)

      val result = removeCollaborator(controller, repoName, owner, "nonexistentuserid")
      result.header.status must equal(303)
    }

    "Remove collaborator who are not a member" in {
      val owner    = createUser()
      val repoName = getRandomString
      createRepository(controller, repoName, owner)
      val collaborator = createUser()

      val result = removeCollaborator(controller, repoName, owner, collaborator.userName)
      result.header.status must equal(303)
    }
  }
}
