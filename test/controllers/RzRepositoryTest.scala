package controllers

import play.api.test.Helpers.{ await, defaultAwaitTimeout, LOCATION }
import templates.controllers.routes

class RzRepositoryTest extends GenericControllerTest {
  "Create Repository" in {
    val account  = createAccount()
    val repoName = getRandomString

    val (result, _) = createRepository(repoName, account)
    result.header.headers(LOCATION) must equal(
      routes.TemplateController.overview(account.a.username, repoName).toString
    )

    val r = await(rzGitRepository.getByOwnerAndName(account.a.username, repoName))
    r.isRight must equal(true)
  }
}
