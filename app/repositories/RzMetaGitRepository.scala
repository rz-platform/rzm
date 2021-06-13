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
  def createRepo(
    repo: RzRepository,
    role: Role,
    repoConfig: RzRepositoryConfig,
    name: PersistentEntityString
  ): Future[_] = Future {
    redis.withClient { client =>
      client.pipeline { f: client.PipelineClient =>
        f.hmset(repo.key, repo.toMap)
        f.hmset(repoConfig.key, repoConfig.toMap)
        f.set(name.key, name.value)
        f.zadd(AccountProjects.key(repo.owner), repo.createdAt.toDouble, repo.id)
        f.hset(RepositoryCollaborators.asKey(repo), repo.owner.id, role.perm)
      }
    }
  }

  def updateRepo(repo: RzRepository): Future[_] = Future {
    redis.withClient(client => client.hset(repo.key, "updatedAt", RzDateTime.now))
  }

  def setRzRepoLastFile(lastOpenedFile: PersistentEntityString): Future[Boolean] = Future {
    redis.withClient(client => client.set(lastOpenedFile.key, lastOpenedFile.value, expire = Duration(30, "days")))
  }

  def getRzRepoLastFile(account: Account, repo: RzRepository): Future[Option[String]] = Future {
    redis.withClient(client => client.get(LastOpenedFile.asKey(account, repo)))
  }

  def setRzRepoConf(config: RzRepositoryConfig): Future[Boolean] = Future {
    redis.withClient(client => client.hmset(config.id, config.toMap))
  }

  def getByRepositoryId(id: String, owner: Account, client: RedisClient): Either[RzError, RzRepository] =
    client.hgetall[String, String](RzRepository.key(id)) match {
      case Some(data) if data.nonEmpty => RzRepository.make(id, owner, data).toRight(ParsingError)

      case _ => Left(NotFoundInRepository)
    }

  def getByRepositoryId(id: String, client: RedisClient): Either[RzError, RzRepository] =
    client.hgetall[String, String](RzRepository.key(id)) match {
      case Some(data) if data.nonEmpty && data.contains("owner") =>
        accountRepository.getById(data.get("owner"), client) match {
          case Right(owner) => RzRepository.make(id, owner, data).toRight(ParsingError)
          case _            => Left(NotFoundInRepository)
        }
      case _ => Left(NotFoundInRepository)
    }

  def getByOwnerAndName(owner: String, name: String, client: RedisClient): Either[RzError, RzRepository] =
    accountRepository.getByName(owner, client) match {
      case Right(owner) =>
        client.get(RepositoryName.asKey(owner.id, name)) match {
          case Some(id: String) => getByRepositoryId(id, owner, client)
          case _                => Left(NotFoundInRepository)
        }
      case _ => Left(NotFoundInRepository)
    }

  def getByOwnerAndName(owner: String, name: String): Future[Either[RzError, RzRepository]] =
    Future(redis.withClient(client => getByOwnerAndName(owner, name, client)))

  def listRepositories(account: Account): Future[List[RzRepository]] = Future {
    redis.withClient { client =>
      client.zrange(AccountProjects.key(account)) match {
        case Some(r: List[String]) =>
          r.map { id: String => getByRepositoryId(id, client) }.collect {
            case Right(value) => value
          }
        case None => List()
      }
    }
  }

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
