package documents.repositories

import collaborators.models.Role
import documents.models.{ RepositoryName, RzRepository, RzRepositoryConfig }
import documents.services.GitService
import users.models.Account

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class DocumentsRepository @Inject() (git: GitService, metaGitRepository: RzMetaGitRepository)(
  implicit ec: ExecutionContext
) {
  def create(owner: Account, name: String): Future[RzRepository] = {
    val repo         = new RzRepository(owner, name)
    val nameRelation = RepositoryName.asEntity(repo)
    val conf         = RzRepositoryConfig.makeDefault(repo, None, None, None)
    for {
      _ <- metaGitRepository.createRepo(repo, Role.Owner, conf, nameRelation)
      _ <- git.initRepo(repo)
    } yield repo
  }

  def buildNewName(owner: Account, templateId: String): Future[String] = {
    metaGitRepository.numberOfRepositories(owner).map {
      case Right(n) => RzRepository.newName(n, templateId)
      case _ => RzRepository.newName(1, templateId)
    }
  }
}
