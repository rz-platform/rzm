package repositories

import com.redis.RedisClient
import models.{ Account, HashedString, SshKey }

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class AccountRepository @Inject() (r: Redis)(implicit ec: ExecutionContext) {
  private val logger = play.api.Logger(this.getClass)

  def getById(id: String, client: RedisClient): Either[RzError, Account] =
    client.hgetall[String, String](id) match {
    case Some(account) => Account.make(id, account)
    case None          => Left(NotFoundInRepository)
  }

  def getById(id: String): Future[Either[RzError, Account]] = Future {
    r.clients.withClient { client =>
      getById(id, client)
    }
  }

  def getByEmailId(emailId: String): Future[Either[RzError, Account]] = Future {
    r.clients.withClient { client =>
      client.get(emailId) match {
        case Some(id: String) => getById(id, client)
        case None             => Left(NotFoundInRepository)
      }
    }
  }

  def getByUsernameOrEmail(s: String): Future[Either[RzError, Account]] =
    getById(Account.id(s)).flatMap {
      case Right(account) => Future(Right(account))
      case _              => getByEmailId(Account.emailId(s))
    }

  def set(account: Account, password: HashedString): Future[Option[List[Any]]] = Future {
    r.clients.withClient(client =>
      client.pipeline { f =>
        f.hmset(account.id, account.toMap)
        f.set(account.emailId, account.id)
        f.set(account.passwordId, password.toString)
      }
    )
  }

  def update(account: Account): Future[Boolean] = Future {
    r.clients.withClient(client => client.hmset(account.id, account.toMap))
  }

  def setPassword(account: Account, hash: String): Future[Boolean] = Future {
    r.clients.withClient(client => client.set(account.passwordId, hash))
  }

  def getPassword(account: Account): Future[Either[RzError, String]] = Future {
    r.clients.withClient { client =>
      client.get(account.passwordId) match {
        case Some(s) => Right(s)
        case None    => Left(NotFoundInRepository)
      }
    }
  }

  def setSshKey(account: Account, key: SshKey): Future[Option[List[Any]]] = Future {
    r.clients.withClient { client =>
      client.pipeline { f =>
        f.hmset(key.id, key.toMap)
        f.zadd(account.sshKeysListId, key.createdAt, key.id)
      }
    }
  }

  def deleteSshKey(account: Account, keyId: String): Future[Option[List[Any]]] = Future {
    r.clients.withClient { client =>
      client.pipeline { f =>
        f.zrem(account.sshKeysListId, keyId)
        f.del(keyId)
      }
    }
  }

  def removePicture(account: Account): Future[Option[Long]] = Future {
    r.clients.withClient(client => client.hdel(account.id, "picture"))
  }

  def setPicture(account: Account, filename: String): Future[Boolean] = Future {
    r.clients.withClient(client => client.hset(account.id, "picture", filename))
  }

  private def getSshKey(id: String, account: Account, client: RedisClient) = client.hgetall(id) match {
    case Some(m) =>
      SshKey.make(m, account) match {
        case Right(s) => Some(s)
        case Left(_)  => None
      }
    case None => None
  }

  def listSshKeys(account: Account): Future[List[SshKey]] = Future {
    r.clients.withClient { client =>
      client.zrange(account.sshKeysListId) match {
        case Some(l: List[String]) => l.flatMap(id => getSshKey(id, account, client))
        case None                  => List()
      }
    }
  }

  /**
   * Number of Ssh keys on Account
   * */
  def cardinalitySshKey(account: Account): Future[Either[RzError, Long]] = Future {
    r.clients.withClient { client =>
      client.zcard(account.sshKeysListId) match {
        case Some(s: Long) => Right(s)
        case None          => Left(NotFoundInRepository)
      }
    }
  }
}
