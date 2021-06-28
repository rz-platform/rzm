package controllers

import collaborators.controllers.routes
import collaborators.models.Role
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
    val owner        = createAccount()
    val repoName     = getRandomString
    val (_, repo)    = createRepository(repoName, owner)
    val collaborator = createAccount()
    val result       = addCollaborator(repoName, owner, collaborator.a.username, Role.Editor.perm.toString)

    result.header.status must equal(303)
    val r = await(collaboratorsRepository.getCollaborator(collaborator.a.id, repo))
    r.nonEmpty must equal(true)
  }

  "Add collaborator with wrong data" in {
    val owner        = createAccount()
    val repoName     = getRandomString
    val (_, repo)    = createRepository(repoName, owner)
    val collaborator = createAccount()
    addCollaborator(repoName, owner, "nonexistentusername", Role.Editor.perm.toString)
    val r = await(collaboratorsRepository.getCollaborator(collaborator.a.id, repo))
    r.nonEmpty must equal(false)
  }

  "Add collaborator that already exists" in {
    val owner    = createAccount()
    val repoName = getRandomString
    createRepository(repoName, owner)
    val collaborator = createAccount()
    val result       = addCollaborator(repoName, owner, collaborator.a.username, Role.Editor.perm.toString)

    result.header.status must equal(303)

    addCollaborator(repoName, owner, collaborator.a.username, Role.Editor.perm.toString)
    result.header.status must equal(303)
  }

  "Remove collaborator" in {
    val owner        = createAccount()
    val repoName     = getRandomString
    val (_, repo)    = createRepository(repoName, owner)
    val collaborator = createAccount()

    addCollaborator(repoName, owner, collaborator.a.username, Role.Editor.perm.toString)

    val result = removeCollaborator(repoName, owner, collaborator.a.username)

    result.header.status must equal(303)
    val r = await(collaboratorsRepository.getCollaborator(collaborator.a.id, repo))
    r.nonEmpty must equal(false)
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
