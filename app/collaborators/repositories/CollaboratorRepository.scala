package collaborators.repositories

import collaborators.models.Collaborator
import com.redis.RedisClient
import documents.models.{ RepositoryCollaborators, RzRepository }
import infrastructure.repositories.{ Redis, RzDateTime }
import users.models.{ Account, AccountProjects }
import users.repositories.AccountRepository

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class CollaboratorRepository @Inject() (redis: Redis, accountRepository: AccountRepository)(
  implicit ec: ExecutionContext
) {

  def addCollaborator(collaborator: Account, role: String, repo: RzRepository): Future[_] = Future {
    redis.withClient { client =>
      client.pipeline { f: client.PipelineClient =>
        f.hset(RepositoryCollaborators.asKey(repo), collaborator.id, role)
        f.zadd(AccountProjects.key(collaborator), RzDateTime.now.toDouble, repo.id)
      }
    }
  }

  def removeCollaborator(account: Account, repo: RzRepository): Future[_] =
    Future {
      redis.withClient { client =>
        client.pipeline { f: client.PipelineClient =>
          f.hdel(RepositoryCollaborators.asKey(repo), account.id)
          f.zrem(AccountProjects.key(account), repo.id)
        }
      }
    }

  def isAccountCollaborator(account: Account, repo: RzRepository): Future[Boolean] =
    Future {
      redis.withClient { client =>
        client.hgetall(RepositoryCollaborators.asKey(repo)) match {
          case Some(m) if m.contains(account.id) => true
          case _                                 => false
        }
      }
    }

  def getCollaborator(accountId: String, repo: RzRepository): Future[Option[Collaborator]] = Future {
    redis.withClient { client =>
      client.hget(RepositoryCollaborators.asKey(repo), accountId) match {
        case Some(permission) => getCollaborator(accountId, repo, permission, client)
        case _                => None
      }
    }
  }

  private def getCollaborator(
    accountId: String,
    repo: RzRepository,
    perm: String,
    client: RedisClient
  ): Option[Collaborator] =
    accountRepository.getById(accountId, client) match {
      case Right(account) => Collaborator.make(account, repo, perm)
      case Left(_)        => None
    }

  def getCollaborators(repo: RzRepository): Future[List[Collaborator]] = Future {
    redis.withClient { client =>
      client.hgetall(RepositoryCollaborators.asKey(repo)) match {
        case Some(data) =>
          data.flatMap {
            case (id, perm) => getCollaborator(id, repo, perm, client)
          }.toList
        case _ => List()
      }
    }
  }
}
