package repositories

import com.redis.RedisClient
import models.{ Account, Collaborator, RzRepository }

import javax.inject.{ Inject, Singleton }

@Singleton
class RzGitRepository @Inject() (r: Redis, accountRepository: AccountRepository) {
  def setRzRepo(repo: RzRepository, author: Collaborator): Option[List[Any]] = r.clients.withClient { client =>
    client.pipeline { f: client.PipelineClient =>
      f.hmset(repo.id, repo.toMap)
      f.zadd(repo.owner.projectListId, repo.createdAt, repo.id)

      f.hmset(author.keyAccessLevel(repo), author.toMap)
    }
  }

  def getByRepositoryId(id: String): Either[RzError, RzRepository] = {
    val (ownerName, repoName) = RzRepository.parseId(id)
    getByOwnerAndName(ownerName, repoName)
  }

  def getByOwnerAndName(owner: String, name: String): Either[RzError, RzRepository] =
    accountRepository.getById(Account.id(owner)) match {
      case Right(account) =>
        r.clients.withClient { client =>
          client.hgetall[String, String](RzRepository.id(owner, name)) match {
            case Some(m) if m.nonEmpty => RzRepository.make(name, account, m)
            case _                     => Left(NotFoundInRepository)
          }
        }
      case Left(e) => Left(e) //e
    }

  def listRepositories(account: Account): List[RzRepository] = r.clients.withClient { client =>
    client.zrange(account.projectListId) match {
      case Some(r: List[String]) =>
        r.map { id: String => getByRepositoryId(id) }.collect {
          case Right(value) => value
        }
      case None => List()
    }
  }

  def addCollaborator(c: Collaborator, repo: RzRepository): Option[List[Any]] = r.clients.withClient { client =>
    client.pipeline { f: client.PipelineClient =>
      f.zadd(repo.collaboratorsListId, c.createdAt, c.account.id)
      f.zadd(c.account.projectListId, c.createdAt, repo.id)
      f.hmset(c.keyAccessLevel(repo), c.toMap)
    }
  }

  def removeCollaborator(account: Account, repo: RzRepository): Option[List[Any]] =
    r.clients.withClient { client =>
      client.pipeline { f: client.PipelineClient =>
        f.zrem(repo.collaboratorsListId, account.id)
        f.zrem(account.projectListId, repo.id)
        f.del(Collaborator.keyAccessLevel(account, repo))
      }
    }

  def isAccountCollaborator(account: Account, repo: RzRepository): Boolean = r.clients.withClient { client =>
    client.hgetall(Collaborator.keyAccessLevel(account, repo)) match {
      case Some(_) => true
      case None    => false
    }
  }

  def getCollaborator(account: Account, repo: RzRepository): Either[RzError, Collaborator] = r.clients.withClient {
    client =>
      client.hgetall(Collaborator.keyAccessLevel(account, repo)) match {
        case Some(m: Map[String, String]) => Collaborator.make(account, m)
        case None                         => Left(NotFoundInRepository)
      }
  }

  def getCollaborators(repo: RzRepository): List[Collaborator] = r.clients.withClient { client =>
    client.zrange(repo.collaboratorsListId) match {
      case Some(r: List[String]) =>
        r.map(aId => accountRepository.getById(aId)).flatMap(a => getCollaboratorByAccount(a, repo, client))
      case _ => List()
    }
  }

  private def getCollaboratorByAccount(
    account: Either[RzError, Account],
    repo: RzRepository,
    client: RedisClient
  ): Option[Collaborator] =
    account match {
      case Right(account) =>
        client.hgetall(Collaborator.keyAccessLevel(account, repo)) match {
          case Some(m) =>
            Collaborator.make(account, m) match {
              case Right(c) => Some(c)
              case Left(_)  => None
            }
          case _ => None
        }
      case _ => None
    }
}
