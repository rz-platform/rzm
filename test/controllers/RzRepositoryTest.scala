package controllers

import play.api.test.Helpers.{ LOCATION, await, defaultAwaitTimeout }

class RzRepositoryTest extends GenericControllerTest {
  "Create Repository" in {
    val account  = createAccount()
    val repoName = getRandomString

    val (result, _) = createRepository(repoName, account)
    result.header.headers(LOCATION) must equal(
      routes.TemplateController.overview(account.a.userName, repoName).toString
    )

    val r = await(rzGitRepository.getByOwnerAndName(account.a.userName, repoName))
    r.isRight must equal(true)
  }
}
