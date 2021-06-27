package ssh.repositories

import com.redis.RedisClient
import infrastructure.errors.{ NotFoundInRepository, RepositoryError }
import infrastructure.repositories.Redis
import ssh.models.SshKey
import users.models.{ Account, AccountSshKeys }
import users.repositories.AccountRepository

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class SshKeyRepository @Inject() (redis: Redis, accountRepository: AccountRepository)(implicit ec: ExecutionContext) {

  def setSshKey(account: Account, key: SshKey): Future[Option[List[Any]]] = Future {
    redis.withClient { client =>
      client.pipeline { f =>
        f.hmset(key.key, key.toMap)
        f.zadd(AccountSshKeys.key(account), key.createdAt.toDouble, key.id)
      }
    }
  }

  def deleteSshKey(account: Account, keyId: String): Future[Option[List[Any]]] = Future {
    redis.withClient { client =>
      client.pipeline { f =>
        f.zrem(AccountSshKeys.key(account), keyId)
        f.del(keyId)
      }
    }
  }

  def getSshKey(id: String): Future[Option[SshKey]] = Future {
    redis.withClient { client =>
      client.hgetall(SshKey.key(id)) match {
        case Some(m) if m.contains("owner") =>
          accountRepository.getById(m.get("owner"), client) match {
            case Right(owner: Account) => SshKey.make(m, owner)
            case _                     => None
          }
        case _ => None
      }
    }
  }

  private def getSshKey(id: String, account: Account, client: RedisClient): Option[SshKey] =
    client.hgetall(SshKey.key(id)) match {
      case Some(m) => SshKey.make(m, account)
      case None    => None
    }

  def listSshKeys(account: Account): Future[List[SshKey]] = Future {
    redis.withClient { client =>
      client.zrange(AccountSshKeys.key(account)) match {
        case Some(l: List[String]) => l.flatMap(id => getSshKey(id, account, client))
        case None                  => List()
      }
    }
  }

  /**
   * Number of Ssh keys on Account
   * */
  def cardinalitySshKey(account: Account): Future[Either[RepositoryError, Long]] = Future {
    redis.withClient { client =>
      client.zcard(AccountSshKeys.key(account)) match {
        case Some(s: Long) => Right(s)
        case None          => Left(NotFoundInRepository)
      }
    }
  }

}
