package controllers

class RzRepositoryTest extends GenericControllerTest {
  "Repository Controller must" {
    "Create Repository" in {
      val account  = createAccount()
      val repoName = getRandomString

      val (result, _) = createRepository(controller, repoName, account)
      result.header.headers(LOCATION) must equal(
        routes.GitEntitiesController.emptyTree(account.userName, repoName, RzRepository.defaultBranch).toString
      )

      val r = await(gitEntitiesRepository.getByOwnerAndName(account.userName, repoName))
      r.isRight must equal(true)
    }
  }
}
