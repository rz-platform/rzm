package controllers

import models.{ Account, EditAccess, RzRepository }
import play.api.mvc.Result
import play.api.test.CSRFTokenHelper.addCSRFToken
import play.api.test.FakeRequest
import play.api.test.Helpers.await

class CollaboratorControllerTest extends GenericControllerTest {
  def addCollaborator(
    controller: FileTreeController,
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
