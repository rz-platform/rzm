package controllers

import models.{ Role, RzRepository }
import play.api.mvc.Result
import play.api.test.CSRFTokenHelper.addCSRFToken
import play.api.test.FakeRequest
import play.api.test.Helpers.{ await, defaultAwaitTimeout }

class CollaboratorControllerTest extends GenericControllerTest {
  def addCollaborator(
    repositoryName: String,
    owner: AuthorizedAccount,
    collaboratorName: String,
    role: String
  ): Result = {
    val request = addCSRFToken(
      FakeRequest(routes.CollaboratorsController.add(owner.a.username, repositoryName))
        .withFormUrlEncodedBody("emailOrLogin" -> collaboratorName, "role" -> role)
        .withSession(owner.s)
        .withCookies(owner.c)
    )

    await(collaboratorsController.add(owner.a.username, repositoryName).apply(request))
  }

  def removeCollaborator(
    repositoryName: String,
    owner: AuthorizedAccount,
    collaboratorName: String
  ): Result = {
    val request = addCSRFToken(
      FakeRequest(routes.CollaboratorsController.remove(owner.a.username, repositoryName))
        .withFormUrlEncodedBody("email" -> collaboratorName)
        .withSession(owner.s)
        .withCookies(owner.c)
    )

    await(collaboratorsController.remove(owner.a.username, repositoryName).apply(request))
  }

  "Add collaborator successfully" in {
    val owner    = createAccount()
    val repoName = getRandomString
    createRepository(repoName, owner)
    val collaborator = createAccount()
    val result       = addCollaborator(repoName, owner, collaborator.a.username, Role.Editor.name)

    result.header.status must equal(303)
    val r = await(rzGitRepository.getCollaborator(collaborator.a, new RzRepository(owner.a, repoName)))
    r.isRight must equal(true)
    // TODO: check
  }

  "Add collaborator with wrong data" in {
    val owner    = createAccount()
    val repoName = getRandomString
    createRepository(repoName, owner)
    val collaborator = createAccount()
    addCollaborator(repoName, owner, "nonexistentusername", Role.Editor.name)
    val r = await(rzGitRepository.getCollaborator(collaborator.a, new RzRepository(owner.a, repoName)))
    r.isRight must equal(false)
  }

  "Add collaborator that already exists" in {
    val owner    = createAccount()
    val repoName = getRandomString
    createRepository(repoName, owner)
    val collaborator = createAccount()
    val result       = addCollaborator(repoName, owner, collaborator.a.username, Role.Editor.name)

    result.header.status must equal(303)

    addCollaborator(repoName, owner, collaborator.a.username, Role.Editor.name)
    result.header.status must equal(303)
  }

  "Remove collaborator" in {
    val owner    = createAccount()
    val repoName = getRandomString
    createRepository(repoName, owner)
    val collaborator = createAccount()

    addCollaborator(repoName, owner, collaborator.a.username, Role.Editor.name)

    val result = removeCollaborator(repoName, owner, collaborator.a.username)

    result.header.status must equal(303)
    val r = await(rzGitRepository.getCollaborator(collaborator.a, new RzRepository(owner.a, repoName)))
    r.isRight must equal(false)
  }

  "Remove collaborator with non-existent userid" in {
    val owner    = createAccount()
    val repoName = getRandomString
    createRepository(repoName, owner)

    val result = removeCollaborator(repoName, owner, "nonexistentuserid")
    result.header.status must equal(303)
  }

  "Remove collaborator who are not a member" in {
    val owner    = createAccount()
    val repoName = getRandomString
    createRepository(repoName, owner)
    val collaborator = createAccount()

    val result = removeCollaborator(repoName, owner, collaborator.a.username)
    result.header.status must equal(303)
  }
}
