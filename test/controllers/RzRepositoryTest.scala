package controllers

import models.RzRepository
import play.api.test.Helpers.{ await, defaultAwaitTimeout, LOCATION }

class RzRepositoryTest extends GenericControllerTest {
  "Create Repository" in {
    val account  = createAccount()
    val repoName = getRandomString

    val (result, _) = createRepository(repoName, account)
    result.header.headers(LOCATION) must equal(
      routes.FileTreeController.emptyTree(account.userName, repoName, RzRepository.defaultBranch).toString
    )

    val r = await(rzGitRepository.getByOwnerAndName(account.userName, repoName))
    r.isRight must equal(true)
  }
}
