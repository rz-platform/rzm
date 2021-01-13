package controllers

import akka.actor.ActorSystem
import models._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest.{ BeforeAndAfterAll, PrivateMethodTester }
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.Configuration
import play.api.db.evolutions.Evolutions
import play.api.db.{ DBApi, Database }
import play.api.mvc.Result
import play.api.test.CSRFTokenHelper._
import play.api.test.Helpers._
import play.api.test._
import repositories.{ AccountRepository, GitEntitiesRepository, GitRepository }

import scala.concurrent.Future

class FunctionalGitEntitiesTest
    extends PlaySpec
    with BeforeAndAfterAll
    with GuiceOneAppPerSuite
    with Injecting
    with PrivateMethodTester
    with ScalaFutures {
  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  private val logger = play.api.Logger(this.getClass)

  implicit val sys: ActorSystem = ActorSystem("RepositoryTest")

  def getRandomString: String =
    java.util.UUID.randomUUID.toString.replace("-", "")

  def databaseApi: DBApi                   = app.injector.instanceOf[DBApi]
  def config: Configuration                = app.injector.instanceOf[Configuration]
  def controller: GitEntitiesController    = app.injector.instanceOf[GitEntitiesController]
  def AccountController: AccountController = app.injector.instanceOf[AccountController]

  def accountRepository: AccountRepository         = app.injector.instanceOf[AccountRepository]
  def gitEntitiesRepository: GitEntitiesRepository = app.injector.instanceOf[GitEntitiesRepository]
  def git: GitRepository                           = app.injector.instanceOf[GitRepository]

  val defaultDatabase: Database = databaseApi.database("default")

  val defaultBranch = RzRepository

  val sessionName: String = SessionName.toString

  override def beforeAll(): Unit =
    Evolutions.applyEvolutions(databaseApi.database("default"))

  // override def afterAll(): Unit = Evolutions.cleanupEvolutions(databaseApi.database("default"))

  def createAccount(): SimpleAccount = {
    val userName = getRandomString
    val request = addCSRFToken(
      FakeRequest(routes.AccountController.saveAccout())
        .withFormUrlEncodedBody(
          "userName"    -> userName,
          "fullName"    -> getRandomString,
          "password"    -> getRandomString,
          "mailAddress" -> s"$getRandomString@rzm.dev"
        )
    )
    await(AccountController.saveAccount().apply(request))
    defaultDatabase.withConnection { connection =>
      val rs = connection.prepareStatement(s"select id from account where username='$userName'").executeQuery()
      rs.next()
      SimpleAccount(rs.getInt(1), userName, null, hasPicture = false)
    }
  }

  def createRepository(
    controller: GitEntitiesController,
    name: String,
    owner: SimpleAccount
  ): (Result, RzRepository) = {
    val request = addCSRFToken(
      FakeRequest(routes.GitEntitiesController.saveRepository())
        .withFormUrlEncodedBody("name" -> name, "description" -> getRandomString)
        .withSession((sessionName, owner.id.toString))
    )
    val result = await(controller.saveRepository().apply(request))
    (result, RzRepository(0, owner, name, RzRepository.defaultBranchName, Some("")))
  }

  def addCollaborator(
    controller: GitEntitiesController,
    repositoryName: String,
    owner: SimpleAccount,
    collaboratorName: String,
    accessLevel: String
  ): Result = {
    val request = addCSRFToken(
      FakeRequest(routes.GitEntitiesController.addCollaborator(owner.userName, repositoryName))
        .withFormUrlEncodedBody("emailOrLogin" -> collaboratorName, "accessLevel" -> accessLevel)
        .withSession((sessionName, owner.id.toString))
    )

    await(controller.addCollaborator(owner.userName, repositoryName).apply(request))
  }

  def removeCollaborator(
    controller: GitEntitiesController,
    repositoryName: String,
    owner: SimpleAccount,
    collaboratorName: String
  ): Result = {
    val request = addCSRFToken(
      FakeRequest(routes.GitEntitiesController.removeCollaborator(owner.userName, repositoryName))
        .withFormUrlEncodedBody("email" -> collaboratorName)
        .withSession((sessionName, owner.id.toString))
    )

    await(controller.removeCollaborator(owner.userName, repositoryName).apply(request))
  }

  def createNewItem(
    controller: GitEntitiesController,
    name: String,
    repositoryName: String,
    creator: SimpleAccount,
    isFolder: Boolean,
    path: String
  ): Result = {
    val newFileRequest = addCSRFToken(
      FakeRequest()
        .withFormUrlEncodedBody(
          "name" -> name,
          "rev"  -> RzRepository.defaultBranchName,
          "path" -> path,
          "isFolder" -> (if (isFolder) "true"
                         else "false")
        )
        .withSession((sessionName, creator.id.toString))
    )

    val result: Future[Result] = call(controller.addNewItem(creator.userName, repositoryName), newFileRequest)

    await(result)
  }

  def listFileInRepo(repository: RzRepository, path: String): RepositoryGitData =
    git.fileList(repository, path = path).getOrElse(RepositoryGitData(List(), None))

  "AccountController" must {
    "Attempt to create user with bad name" in {
      val badUserNames =
        List("&", "!", "%", "киррилица", "with space", "^", "@", "--++", "::", "t)", "exceededlength" * 10)
      badUserNames.map { username =>
        val request = addCSRFToken(
          FakeRequest(routes.AccountController.saveAccout())
            .withFormUrlEncodedBody(
              "userName"    -> username,
              "fullName"    -> getRandomString,
              "password"    -> getRandomString,
              "mailAddress" -> s"$getRandomString@rzm.dev"
            )
        )

        val result = await(AccountController.saveAccount().apply(request))
        result.header.status must equal(400)
      }
    }

    "Successful attempt to create user" in {
      val goodUserNames = List(
        getRandomString,
        "with_underscore" + getRandomString.take(4),
        "with-minus" + getRandomString.take(4),
        "MiXeDcAse" + getRandomString.take(4)
      )
      goodUserNames.map { username =>
        val request = addCSRFToken(
          FakeRequest(routes.AccountController.saveAccout())
            .withFormUrlEncodedBody(
              "userName"    -> username,
              "fullName"    -> getRandomString,
              "password"    -> getRandomString,
              "mailAddress" -> s"$getRandomString@rzm.dev"
            )
        )

        val result = await(AccountController.saveAccount().apply(request))
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

  "GitEntitiesController" must {
    "Create Repository" in {
      val account  = createAccount()
      val repoName = getRandomString

      val (result, _) = createRepository(controller, repoName, account)
      result.header.headers(LOCATION) must equal(routes.GitEntitiesController.list().toString)

      defaultDatabase.withConnection { connection =>
        val isRepoExist = connection
          .prepareStatement(s"select 1 FROM repository WHERE name='$repoName'")
          .executeQuery()
        isRepoExist.next() must equal(true)
      }
    }

    "Create file in repository" in {
      val account  = createAccount()
      val repoName = getRandomString
      val fileName = getRandomString

      val (_, r) = createRepository(controller, repoName, account)

      createNewItem(controller, fileName, repoName, account, isFolder = false, ".")

      val listRootFiles = listFileInRepo(r, ".")

      listRootFiles.files.exists(_.name contains fileName) must equal(true)
    }

    "Create folder in repository" in {
      val account    = createAccount()
      val repoName   = getRandomString
      val folderName = getRandomString

      val (_, r) = createRepository(controller, repoName, account)

      createNewItem(controller, folderName, repoName, account, isFolder = true, ".")

      val rootFileList = listFileInRepo(r, ".")

      rootFileList.files.exists(_.name contains folderName) must equal(true)

      val folderFileList = listFileInRepo(r, folderName)
      folderFileList.files.exists(_.name contains ".gitkeep") must equal(true)
    }

    "Attempt to create file with forbidden symbols" in {
      val account = createAccount()

      val repoName = getRandomString
      val fileName = getRandomString + ForbiddenSymbols.toString

      val (_, r) = createRepository(controller, repoName, account)

      val result = createNewItem(controller, fileName, repoName, account, isFolder = false, ".")
      result.header.status must equal(303)
      val folderFileList = listFileInRepo(r, ".")
      folderFileList.files.exists(_.name contains fileName) must equal(false)
    }

    "Attempt to create folder with forbidden symbols" in {
      val account = createAccount()

      val repoName   = getRandomString
      val folderName = getRandomString + ForbiddenSymbols.toString

      val (_, r) = createRepository(controller, repoName, account)

      val result = createNewItem(controller, folderName, repoName, account, isFolder = true, ".")
      result.header.status must equal(303)
      val folderFileList = listFileInRepo(r, ".")
      folderFileList.files.exists(_.name contains folderName) must equal(false)
    }

    "Create file in subfolder" in {
      val account = createAccount()

      val repoName            = getRandomString
      val folderName          = getRandomString
      val fileInSubfolderName = getRandomString

      val (_, r) = createRepository(controller, repoName, account)

      createNewItem(controller, folderName, repoName, account, isFolder = true, ".")
      createNewItem(controller, fileInSubfolderName, repoName, account, isFolder = false, folderName)

      val folderFileList = listFileInRepo(r, folderName)
      folderFileList.files.exists(_.name contains fileInSubfolderName) must equal(true)
    }

    "Create folder in subfolder" in {
      val account = createAccount()

      val repoName              = getRandomString
      val folderName            = getRandomString
      val folderInSubfolderName = getRandomString

      val (_, r) = createRepository(controller, repoName, account)

      createNewItem(controller, folderName, repoName, account, isFolder = true, ".")
      createNewItem(controller, folderInSubfolderName, repoName, account, isFolder = true, folderName)

      val folderFileList = listFileInRepo(r, folderName)
      folderFileList.files.exists(_.name contains folderInSubfolderName) must equal(true)
    }

    "Create folder with spaces in subfolder" in {
      val account = createAccount()

      val repoName              = getRandomString
      val folderName            = getRandomString + " " + getRandomString
      val folderInSubfolderName = getRandomString + " " + getRandomString

      val (_, r) = createRepository(controller, repoName, account)

      createNewItem(controller, folderName, repoName, account, isFolder = true, ".")
      createNewItem(controller, folderInSubfolderName, repoName, account, isFolder = true, folderName)

      val folderFileList = listFileInRepo(r, folderName)
      folderFileList.files.exists(_.name contains folderInSubfolderName) must equal(true)
    }

    "Create file with spaces in subfolder" in {
      val account = createAccount()

      val repoName            = getRandomString
      val folderName          = getRandomString + " " + getRandomString
      val fileInSubfolderName = getRandomString + " " + getRandomString

      val (_, r) = createRepository(controller, repoName, account)

      createNewItem(controller, folderName, repoName, account, isFolder = true, ".")
      createNewItem(controller, fileInSubfolderName, repoName, account, isFolder = false, folderName)

      val folderFileList = listFileInRepo(r, folderName)
      folderFileList.files.exists(_.name contains fileInSubfolderName) must equal(true)
    }

    "Create file with non-ascii name" in {
      val nonAsciiNames = List("ÇŞĞIİÖÜ", "测试", "кирилицца", "ظضذخثتش")
      nonAsciiNames.map { name =>
        val account  = createAccount()
        val repoName = getRandomString
        val (_, r)   = createRepository(controller, repoName, account)
        createNewItem(controller, name, repoName, account, isFolder = false, ".")
        val folderFileList = listFileInRepo(r, ".")
        folderFileList.files.exists(_.name contains name) must equal(true)
      }
    }

    "Create folder with non-ascii name" in {
      val nonAsciiNames = List("ÇŞĞIİÖÜ", "测试", "кирилицца", "ظضذخثتش")
      nonAsciiNames.map { name =>
        val account  = createAccount()
        val repoName = getRandomString
        val (_, r)   = createRepository(controller, repoName, account)
        createNewItem(controller, name, repoName, account, isFolder = true, ".")
        val folderFileList = listFileInRepo(r, ".")
        folderFileList.files.exists(_.name contains name) must equal(true)
      }
    }

    "Add collaborator successfully" in {
      val owner    = createAccount()
      val repoName = getRandomString
      createRepository(controller, repoName, owner)
      val collaborator = createAccount()
      val result       = addCollaborator(controller, repoName, owner, collaborator.userName, EditAccess.toString)

      result.header.status must equal(303)
      defaultDatabase.withConnection { connection =>
        val collaboratorStatement = connection
          .prepareStatement(
            s"select 1 FROM collaborator WHERE account_id=${collaborator.id}" +
              s"and repository_id=(select id FROM repository WHERE name='$repoName')" +
              s"and role=${EditAccess.role}"
          )
          .executeQuery()
        collaboratorStatement.next() must equal(true)
      }
    }

    "Add collaborator with wrong data" in {
      val owner    = createAccount()
      val repoName = getRandomString
      createRepository(controller, repoName, owner)
      val collaborator = createAccount()
      addCollaborator(controller, repoName, owner, "nonexistentusername", EditAccess.toString)

      defaultDatabase.withConnection { connection =>
        val isCollaboratorExist = connection
          .prepareStatement(
            s"select 1 FROM collaborator WHERE account_id=${collaborator.id} and " +
              s"repository_id=(select id FROM repository WHERE name='$repoName')"
          )
          .executeQuery()
        isCollaboratorExist.next() must equal(false)
      }
    }

    "Add collaborator that already exists" in {
      val owner    = createAccount()
      val repoName = getRandomString
      createRepository(controller, repoName, owner)
      val collaborator = createAccount()
      val result       = addCollaborator(controller, repoName, owner, collaborator.userName, EditAccess.toString)

      result.header.status must equal(303)
      defaultDatabase.withConnection { connection =>
        val collaboratorStatement = connection
          .prepareStatement(
            s"select 1 FROM collaborator WHERE account_id=${collaborator.id}" +
              s"and repository_id=(select id FROM repository WHERE name='$repoName')" +
              s"and role=${EditAccess.role}"
          )
          .executeQuery()
        collaboratorStatement.next() must equal(true)
      }

      addCollaborator(controller, repoName, owner, collaborator.userName, EditAccess.toString)
      result.header.status must equal(303)
    }

    "Remove collaborator" in {
      val owner    = createAccount()
      val repoName = getRandomString
      createRepository(controller, repoName, owner)
      val collaborator = createAccount()

      addCollaborator(controller, repoName, owner, collaborator.userName, EditAccess.toString)
      defaultDatabase.withConnection { connection =>
        val collaboratorStatement = connection
          .prepareStatement(
            s"select 1 FROM collaborator WHERE account_id=${collaborator.id}" +
              s"and repository_id=(select id FROM repository WHERE name='$repoName')" +
              s"and role=${EditAccess.role}"
          )
          .executeQuery()
        collaboratorStatement.next() must equal(true)
      }

      val result = removeCollaborator(controller, repoName, owner, collaborator.userName)

      result.header.status must equal(303)
      defaultDatabase.withConnection { connection =>
        val collaboratorStatement = connection
          .prepareStatement(
            s"select 1 FROM collaborator WHERE account_id=${collaborator.id}" +
              s"and repository_id=(select id FROM repository WHERE name='$repoName')"
          )
          .executeQuery()
        collaboratorStatement.next() must equal(false)
      }
    }

    "Remove collaborator with non-existent userid" in {
      val owner    = createAccount()
      val repoName = getRandomString
      createRepository(controller, repoName, owner)

      val result = removeCollaborator(controller, repoName, owner, "nonexistentuserid")
      result.header.status must equal(303)
    }

    "Remove collaborator who are not a member" in {
      val owner    = createAccount()
      val repoName = getRandomString
      createRepository(controller, repoName, owner)
      val collaborator = createAccount()

      val result = removeCollaborator(controller, repoName, owner, collaborator.userName)
      result.header.status must equal(303)
    }
  }
}
