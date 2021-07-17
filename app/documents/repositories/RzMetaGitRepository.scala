package documents.repositories

import collaborators.models.Role
import com.redis.RedisClient
import documents.models._
import infrastructure.errors.{ NotFoundInRepository, ParsingError, RepositoryError }
import infrastructure.models.PersistentEntityString
import infrastructure.repositories.{ Redis, RzDateTime }
import users.models.{ Account, AccountProjects }
import users.repositories.AccountRepository

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

  def getByRepositoryId(id: String, owner: Account, client: RedisClient): Either[RepositoryError, RzRepository] =
    client.hgetall[String, String](RzRepository.key(id)) match {
      case Some(data) if data.nonEmpty => RzRepository.make(id, owner, data).toRight(ParsingError)

      case _ => Left(NotFoundInRepository)
    }

  def getByRepositoryId(id: String, client: RedisClient): Either[RepositoryError, RzRepository] =
    client.hgetall[String, String](RzRepository.key(id)) match {
      case Some(data) if data.nonEmpty && data.contains("owner") =>
        accountRepository.getById(data.get("owner"), client) match {
          case Right(owner) => RzRepository.make(id, owner, data).toRight(ParsingError)
          case _            => Left(NotFoundInRepository)
        }
      case _ => Left(NotFoundInRepository)
    }

  def getByOwnerAndName(owner: String, name: String, client: RedisClient): Either[RepositoryError, RzRepository] =
    accountRepository.getByName(owner, client) match {
      case Right(owner) =>
        client.get(RepositoryName.asKey(owner.id, name)) match {
          case Some(id: String) => getByRepositoryId(id, owner, client)
          case _                => Left(NotFoundInRepository)
        }
      case _ => Left(NotFoundInRepository)
    }

  def getByOwnerAndName(owner: String, name: String): Future[Either[RepositoryError, RzRepository]] =
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

  def numberOfRepositories(account: Account): Future[Either[RepositoryError, Long]] = Future {
    redis.withClient { client =>
      client.zcard(AccountProjects.key(account)) match {
        case Some(s: Long) => Right(s)
        case None          => Left(NotFoundInRepository)
      }
    }
  }
}
