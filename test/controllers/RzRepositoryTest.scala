package controllers

import models.RzRepository
import play.api.test.Helpers.{ LOCATION, await, defaultAwaitTimeout }

class RzRepositoryTest extends GenericControllerTest {
  "Create Repository" in {
    val account  = createAccount()
    val repoName = getRandomString

    val (result, _) = createRepository(repoName, account)
    result.header.headers(LOCATION) must equal(
      routes.FileTreeController.emptyTree(account.a.userName, repoName, RzRepository.defaultBranch).toString
    )

    val r = await(rzGitRepository.getByOwnerAndName(account.a.userName, repoName))
    r.isRight must equal(true)
  }
}
