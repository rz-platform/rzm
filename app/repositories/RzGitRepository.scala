package repositories

import com.redis.RedisClient
import models.{ Account, Collaborator, RzRepository }

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class RzGitRepository @Inject() (r: Redis, accountRepository: AccountRepository)(implicit ec: ExecutionContext) {
  def setRzRepo(repo: RzRepository, author: Collaborator): Future[Option[List[Any]]] = Future {
    r.clients.withClient { client =>
      client.pipeline { f: client.PipelineClient =>
        f.hmset(repo.id, repo.toMap)
        f.zadd(repo.owner.projectListId, repo.createdAt, repo.id)

        f.hmset(author.keyAccessLevel(repo), author.toMap)
      }
    }
  }

  private def getByRepositoryId(id: String, client: RedisClient): Either[RzError, RzRepository] = {
    val (ownerName, repoName) = RzRepository.parseId(id)
    getByOwnerAndName(ownerName, repoName, client)
  }

  def getByOwnerAndName(owner: String, name: String, client: RedisClient): Either[RzError, RzRepository] =
    accountRepository.getById(Account.id(owner), client) match {
      case Right(account) =>
        r.clients.withClient { client =>
          client.hgetall[String, String](RzRepository.id(owner, name)) match {
            case Some(m) if m.nonEmpty => RzRepository.make(name, account, m)
            case _                     => Left(NotFoundInRepository)
          }
        }
      case Left(e) => Left(e)
    }

  def getByOwnerAndName(owner: String, name: String): Future[Either[RzError, RzRepository]] =
    Future(r.clients.withClient(client => getByOwnerAndName(owner, name, client)))

  def listRepositories(account: Account): Future[List[RzRepository]] = Future {
    r.clients.withClient { client =>
      client.zrange(account.projectListId) match {
        case Some(r: List[String]) =>
          r.map { id: String => getByRepositoryId(id, client) }.collect {
            case Right(value) => value
          }
        case None => List()
      }
    }
  }

  def addCollaborator(c: Collaborator, repo: RzRepository): Future[Option[List[Any]]] = Future {
    r.clients.withClient { client =>
      client.pipeline { f: client.PipelineClient =>
        f.zadd(repo.collaboratorsListId, c.createdAt, c.account.id)
        f.zadd(c.account.projectListId, c.createdAt, repo.id)
        f.hmset(c.keyAccessLevel(repo), c.toMap)
      }
    }
  }

  def removeCollaborator(account: Account, repo: RzRepository): Future[Option[List[Any]]] =
    Future {
      r.clients.withClient { client =>
        client.pipeline { f: client.PipelineClient =>
          f.zrem(repo.collaboratorsListId, account.id)
          f.zrem(account.projectListId, repo.id)
          f.del(Collaborator.keyAccessLevel(account, repo))
        }
      }
    }

  def isAccountCollaborator(account: Account, repo: RzRepository): Future[Boolean] =
    Future {
      r.clients.withClient { client =>
        client.hgetall(Collaborator.keyAccessLevel(account, repo)) match {
          case Some(m) if m.nonEmpty => true
          case _                     => false
        }
      }
    }

  def getCollaborator(account: Account, repo: RzRepository): Future[Either[RzError, Collaborator]] =
    Future {
      r.clients.withClient { client =>
        client.hgetall(Collaborator.keyAccessLevel(account, repo)) match {
          case Some(m: Map[String, String]) => Collaborator.make(account, m)
          case None                         => Left(NotFoundInRepository)
        }
      }
    }

  def getCollaborators(repo: RzRepository): Future[List[Collaborator]] = Future {
    r.clients.withClient { client =>
      client.zrange(repo.collaboratorsListId) match {
        case Some(r: List[String]) =>
          r.map(aId => accountRepository.getById(aId, client)).flatMap(a => getCollaboratorByAccount(a, repo, client))
        case _ => List()
      }
    }
  }

  def getCollaboratorByAccount(
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
