package controllers

import documents.models.{ RepositoryGitData, RzRepository }
import infrastructure.validations.ForbiddenSymbols
import play.api.mvc.Result
import play.api.test.CSRFTokenHelper.addCSRFToken
import play.api.test.FakeRequest
import play.api.test.Helpers.{ await, call, defaultAwaitTimeout, writeableOf_AnyContentAsFormUrlEncoded }

import scala.concurrent.Future

class FileTreeControllerTest extends GenericControllerTest {
  def listFileInRepo(repository: RzRepository, path: String): RepositoryGitData =
    await(git.fileList(repository, RzRepository.defaultBranch, path)).getOrElse(RepositoryGitData(List(), None))

  def createNewItem(
    name: String,
    repositoryName: String,
    creator: AuthorizedAccount,
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
        .withSession(creator.s)
        .withCookies(creator.c)
    )

    val result: Future[Result] = call(fileEditController.addNewItem(creator.a.username, repositoryName), newFileRequest)

    await(result)
  }

  "Create file in repository" in {
    val account  = createAccount()
    val repoName = getRandomString
    val fileName = getRandomString

    val (_, r) = createRepository(repoName, account)

    createNewItem(fileName, repoName, account, isFolder = false, ".")

    val listRootFiles = listFileInRepo(r, ".")

    listRootFiles.files.exists(_.name contains fileName) must equal(true)
  }

  "Create folder in repository" in {
    val account    = createAccount()
    val repoName   = getRandomString
    val folderName = getRandomString

    val (_, r) = createRepository(repoName, account)

    createNewItem(folderName, repoName, account, isFolder = true, ".")

    val rootFileList = listFileInRepo(r, ".")

    rootFileList.files.exists(_.name contains folderName) must equal(true)

    val folderFileList = listFileInRepo(r, folderName)
    folderFileList.files.exists(_.name contains ".gitkeep") must equal(true)
  }

  "Attempt to create file with forbidden symbols" in {
    val account = createAccount()

    val repoName = getRandomString
    val fileName = getRandomString + ForbiddenSymbols.toString

    val (_, r) = createRepository(repoName, account)

    val result = createNewItem(fileName, repoName, account, isFolder = false, ".")
    result.header.status must equal(303)
    val folderFileList = listFileInRepo(r, ".")
    folderFileList.files.exists(_.name contains fileName) must equal(false)
  }

  "Attempt to create folder with forbidden symbols" in {
    val account = createAccount()

    val repoName   = getRandomString
    val folderName = getRandomString + ForbiddenSymbols.toString

    val (_, r) = createRepository(repoName, account)

    val result = createNewItem(folderName, repoName, account, isFolder = true, ".")
    result.header.status must equal(303)
    val folderFileList = listFileInRepo(r, ".")
    folderFileList.files.exists(_.name contains folderName) must equal(false)
  }

  "Create file in subfolder" in {
    val account = createAccount()

    val repoName            = getRandomString
    val folderName          = getRandomString
    val fileInSubfolderName = getRandomString

    val (_, r) = createRepository(repoName, account)

    createNewItem(folderName, repoName, account, isFolder = true, ".")
    createNewItem(fileInSubfolderName, repoName, account, isFolder = false, folderName)

    val folderFileList = listFileInRepo(r, folderName)
    folderFileList.files.exists(_.name contains fileInSubfolderName) must equal(true)
  }

  "Create folder in subfolder" in {
    val account = createAccount()

    val repoName              = getRandomString
    val folderName            = getRandomString
    val folderInSubfolderName = getRandomString

    val (_, r) = createRepository(repoName, account)

    createNewItem(folderName, repoName, account, isFolder = true, ".")
    createNewItem(folderInSubfolderName, repoName, account, isFolder = true, folderName)

    val folderFileList = listFileInRepo(r, folderName)
    folderFileList.files.exists(_.name contains folderInSubfolderName) must equal(true)
  }

  "Create folder with spaces in subfolder" in {
    val account = createAccount()

    val repoName              = getRandomString
    val folderName            = getRandomString + " " + getRandomString
    val folderInSubfolderName = getRandomString + " " + getRandomString

    val (_, r) = createRepository(repoName, account)

    createNewItem(folderName, repoName, account, isFolder = true, ".")
    createNewItem(folderInSubfolderName, repoName, account, isFolder = true, folderName)

    val folderFileList = listFileInRepo(r, folderName)
    folderFileList.files.exists(_.name contains folderInSubfolderName) must equal(true)
  }

  "Create file with spaces in subfolder" in {
    val account = createAccount()

    val repoName            = getRandomString
    val folderName          = getRandomString + " " + getRandomString
    val fileInSubfolderName = getRandomString + " " + getRandomString

    val (_, r) = createRepository(repoName, account)

    createNewItem(folderName, repoName, account, isFolder = true, ".")
    createNewItem(fileInSubfolderName, repoName, account, isFolder = false, folderName)

    val folderFileList = listFileInRepo(r, folderName)
    folderFileList.files.exists(_.name contains fileInSubfolderName) must equal(true)
  }

  "Create file with non-ascii name" in {
    val nonAsciiNames = List("ÇŞĞIİÖÜ", "测试", "кирилицца", "ظضذخثتش")
    nonAsciiNames.map { name =>
      val account  = createAccount()
      val repoName = getRandomString
      val (_, r)   = createRepository(repoName, account)
      createNewItem(name, repoName, account, isFolder = false, ".")
      val folderFileList = listFileInRepo(r, ".")
      folderFileList.files.exists(_.name contains name) must equal(true)
    }
  }

  "Create folder with non-ascii name" in {
    val nonAsciiNames = List("ÇŞĞIİÖÜ", "测试", "кирилицца", "ظضذخثتش")
    nonAsciiNames.map { name =>
      val account  = createAccount()
      val repoName = getRandomString
      val (_, r)   = createRepository(repoName, account)
      createNewItem(name, repoName, account, isFolder = true, ".")
      val folderFileList = listFileInRepo(r, ".")
      folderFileList.files.exists(_.name contains name) must equal(true)
    }
  }

}
