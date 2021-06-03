package repositories

import com.redis.RedisClient
import infrastructure.{ Redis, RzDateTime }
import models._

import javax.inject.Inject
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
class RzMetaGitRepository @Inject() (redis: Redis, accountRepository: AccountRepository)(
  implicit ec: ExecutionContext
) {
  def setRzRepo(repo: RzRepository, author: Collaborator, repoConfig: RzRepositoryConfig): Future[_] = Future {
    redis.withClient { client =>
      client.pipeline { f: client.PipelineClient =>
        f.hmset(repo.id, repo.toMap)
        f.hmset(repoConfig.id, repoConfig.toMap)
        f.zadd(repo.owner.projectListKey, repo.createdAt.toDouble, repo.id)
        f.hmset(author.keyAccessLevel(repo), author.toMap)
      }
    }
  }

  def updateRepo(repo: RzRepository): Future[_] = Future {
    redis.withClient(client => client.hset(repo.id, "updatedAt", RzDateTime.now))
  }

  def setRzRepoLastFile(account: Account, repo: RzRepository, lastOpenedFile: String): Future[Boolean] = Future {
    redis.withClient(client =>
      client.set(LastOpenedFile.id(account, repo), lastOpenedFile, expire = Duration(30, "days"))
    )
  }

  def getRzRepoLastFile(account: Account, repo: RzRepository): Future[Option[String]] = Future {
    redis.withClient(client => client.get(LastOpenedFile.id(account, repo)))
  }

  def setRzRepoConf(config: RzRepositoryConfig): Future[Boolean] = Future {
    redis.withClient(client => client.hmset(config.id, config.toMap))
  }

  private def getByRepositoryId(id: String, client: RedisClient): Either[RzError, RzRepository] = {
    val (ownerName, repoName) = RzRepository.parseId(id)
    getByOwnerAndName(ownerName, repoName, client)
  }

  def getByOwnerAndName(owner: String, name: String, client: RedisClient): Either[RzError, RzRepository] =
    accountRepository.getById(Account.id(owner), client) match {
      case Right(account) =>
        redis.withClient { client =>
          client.hgetall[String, String](RzRepository.id(owner, name)) match {
            case Some(m) if m.nonEmpty => RzRepository.make(name, account, m)
            case _                     => Left(NotFoundInRepository)
          }
        }
      case Left(e) => Left(e)
    }

  def getByOwnerAndName(owner: String, name: String): Future[Either[RzError, RzRepository]] =
    Future(redis.withClient(client => getByOwnerAndName(owner, name, client)))

  def listRepositories(account: Account): Future[List[RzRepository]] = Future {
    redis.withClient { client =>
      client.zrange(account.projectListKey) match {
        case Some(r: List[String]) =>
          r.map { id: String => getByRepositoryId(id, client) }.collect {
            case Right(value) => value
          }
        case None => List()
      }
    }
  }

  def addCollaborator(c: Collaborator, repo: RzRepository): Future[_] = Future {
    redis.withClient { client =>
      client.pipeline { f: client.PipelineClient =>
        f.zadd(repo.collaboratorsListId, c.createdAt.toDouble, c.account.key)
        f.zadd(c.account.projectListKey, c.createdAt.toDouble, repo.id)
        f.hmset(c.keyAccessLevel(repo), c.toMap)
      }
    }
  }

  def removeCollaborator(account: Account, repo: RzRepository): Future[_] =
    Future {
      redis.withClient { client =>
        client.pipeline { f: client.PipelineClient =>
          f.zrem(repo.collaboratorsListId, account.key)
          f.zrem(account.projectListKey, repo.id)
          f.del(Collaborator.keyAccessLevel(account, repo))
        }
      }
    }

  def isAccountCollaborator(account: Account, repo: RzRepository): Future[Boolean] =
    Future {
      redis.withClient { client =>
        client.hgetall(Collaborator.keyAccessLevel(account, repo)) match {
          case Some(m) if m.nonEmpty => true
          case _                     => false
        }
      }
    }

  def getCollaborator(account: Account, repo: RzRepository): Future[Either[RzError, Collaborator]] =
    Future {
      redis.withClient { client =>
        client.hgetall(Collaborator.keyAccessLevel(account, repo)) match {
          case Some(m: Map[String, String]) => Collaborator.make(account, m)
          case None                         => Left(NotFoundInRepository)
        }
      }
    }

  def getCollaborators(repo: RzRepository): Future[List[Collaborator]] = Future {
    redis.withClient { client =>
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
