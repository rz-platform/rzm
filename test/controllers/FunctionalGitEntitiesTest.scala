package controllers

import akka.actor.ActorSystem
import models._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, PrivateMethodTester}
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.Configuration
import play.api.mvc.Result
import play.api.test.CSRFTokenHelper._
import play.api.test.Helpers._
import play.api.test._
import repositories.{AccountRepository, GitRepository, RzGitRepository}

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

  def config: Configuration                = app.injector.instanceOf[Configuration]
  def controller: GitEntitiesController    = app.injector.instanceOf[GitEntitiesController]
  def AccountController: AccountController = app.injector.instanceOf[AccountController]

  def accountRepository: AccountRepository         = app.injector.instanceOf[AccountRepository]
  def gitEntitiesRepository: RzGitRepository = app.injector.instanceOf[RzGitRepository]
  def git: GitRepository                           = app.injector.instanceOf[GitRepository]

  val sessionName: String = SessionName.toString

  def createAccount(): Account = {
    val data = AccountRegistrationData(getRandomString, Some(getRandomString), getRandomString, s"$getRandomString@rzm.dev")
    val request = addCSRFToken(
      FakeRequest(routes.AccountController.saveAccount())
        .withFormUrlEncodedBody(
          "userName"    -> data.userName,
          "fullName"    -> data.fullName.get,
          "password"    -> data.password,
          "mailAddress" -> data.email
        )
    )
    await(AccountController.saveAccount().apply(request))
    new Account(data)
  }

  def createRepository(
    controller: GitEntitiesController,
    name: String,
    owner: Account
  ): (Result, RzRepository) = {
    val request = addCSRFToken(
      FakeRequest(routes.GitEntitiesController.saveRepository())
        .withFormUrlEncodedBody("name" -> name, "description" -> getRandomString)
        .withSession((sessionName, owner.id.toString))
    )
    val result = await(controller.saveRepository().apply(request))
    (result, new RzRepository(owner, name))
  }

  def addCollaborator(
    controller: GitEntitiesController,
    repositoryName: String,
    owner: Account,
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
    owner: Account,
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
    creator: Account,
    isFolder: Boolean,
    path: String
  ): Result = {
    val newFileRequest = addCSRFToken(
      FakeRequest()
        .withFormUrlEncodedBody(
          "name" -> name,
          "rev"  -> RzRepository.defaultBranch,
          "path" -> path,
          "isFolder" -> (if (isFolder) "true"
                         else "false")
        )
        .withSession((sessionName, creator.id))
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
          FakeRequest(routes.AccountController.saveAccount())
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
          FakeRequest(routes.AccountController.saveAccount())
            .withFormUrlEncodedBody(
              "userName"    -> username,
              "fullName"    -> getRandomString,
              "password"    -> getRandomString,
              "mailAddress" -> s"$getRandomString@rzm.dev"
            )
        )

        val result = await(AccountController.saveAccount().apply(request))
        result.header.status must equal(303)

        val a = await(accountRepository.getById(Account.id(username.toLowerCase)))
        a.isRight must equal(true)
      }
    }
  }

  "GitEntitiesController" must {
    "Create Repository" in {
      val account  = createAccount()
      val repoName = getRandomString

      val (result, _) = createRepository(controller, repoName, account)
      result.header.headers(LOCATION) must equal(routes.GitEntitiesController.emptyTree(account.userName, repoName, RzRepository.defaultBranch).toString)

      val r = await(gitEntitiesRepository.getByOwnerAndName(account.userName, repoName))
      r.isRight must equal(true)
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
      val r = await(gitEntitiesRepository.getCollaborator(collaborator, new RzRepository(owner, repoName)))
      r.isRight must equal(true)
      // TODO: check
    }

    "Add collaborator with wrong data" in {
      val owner    = createAccount()
      val repoName = getRandomString
      createRepository(controller, repoName, owner)
      val collaborator = createAccount()
      addCollaborator(controller, repoName, owner, "nonexistentusername", EditAccess.toString)
      val r = await(gitEntitiesRepository.getCollaborator(collaborator, new RzRepository(owner, repoName)))
      r.isRight must equal(false)
    }

    "Add collaborator that already exists" in {
      val owner    = createAccount()
      val repoName = getRandomString
      createRepository(controller, repoName, owner)
      val collaborator = createAccount()
      val result       = addCollaborator(controller, repoName, owner, collaborator.userName, EditAccess.toString)

      result.header.status must equal(303)

      addCollaborator(controller, repoName, owner, collaborator.userName, EditAccess.toString)
      result.header.status must equal(303)
    }

    "Remove collaborator" in {
      val owner    = createAccount()
      val repoName = getRandomString
      createRepository(controller, repoName, owner)
      val collaborator = createAccount()

      addCollaborator(controller, repoName, owner, collaborator.userName, EditAccess.toString)

      val result = removeCollaborator(controller, repoName, owner, collaborator.userName)

      result.header.status must equal(303)
      val r = await(gitEntitiesRepository.getCollaborator(collaborator, new RzRepository(owner, repoName)))
      r.isRight must equal(false)
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
