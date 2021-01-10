package repositories

import com.redis.RedisClient
import models.{ Account, HashedString, SshKey }

import javax.inject.{ Inject, Singleton }

@Singleton
class AccountRepository @Inject() (r: Redis) {
  private val logger = play.api.Logger(this.getClass)

  def getById(id: String): Either[RzError, Account] = r.clients.withClient { client =>
    client.hgetall[String, String](id) match { // Account.id(
      case Some(account) => Account.make(id, account)
      case None          => Left(NotFoundInRepository)
    }
  }

  def getByEmailId(emailId: String): Either[RzError, Account] = r.clients.withClient { client =>
    client.get(Account.emailId(emailId)) match {
      case Some(id: String) => getById(id)
      case None             => Left(NotFoundInRepository)
    }
  }

  def getByUsernameOrEmail(s: String): Either[RzError, Account] = // TODO: share one connect
    getById(Account.id(s)) match {
      case Right(account) => Right(account)
      case Left(_)        => getByEmailId(Account.emailId(s))
    }

  def set(account: Account, password: HashedString): Option[List[Any]] =
    r.clients.withClient(client =>
      client.pipeline { f =>
        f.hmset(account.id, account.toMap)
        f.set(account.emailId, account.id)
        f.set(account.passwordId, password.toString)
      }
    )

  def setPassword(account: Account, hash: String): Boolean = r.clients.withClient { client =>
    client.set(account.passwordId, hash)
  }

  def getPassword(account: Account): Either[RzError, String] = r.clients.withClient { client =>
    client.get(account.passwordId) match {
      case Some(s) => Right(s)
      case None    => Left(NotFoundInRepository)
    }
  }

  def setSshKey(account: Account, key: SshKey): Option[Long] = r.clients.withClient { client =>
    client.hmset(key.id, key.toMap)
    client.zadd(account.sshKeysListId, key.createdAt, key.id)
  }

  def deleteSshKey(account: Account, keyId: String): Option[Long] = r.clients.withClient { client =>
    client.zrem(account.sshKeysListId, keyId)
    client.del(keyId)
  }

  def removePicture(account: Account): Option[Long] = r.clients.withClient { client =>
    client.hdel(account.id, "picture")
  }

  def setPicture(account: Account, filename: String): Boolean = r.clients.withClient { client =>
    client.hset(account.id, "picture", filename)
  }

  private def getSshKey(id: String, account: Account, client: RedisClient): Option[SshKey] = client.hgetall(id) match {
    case Some(m) =>
      SshKey.make(m, account) match {
        case Right(s) => Some(s)
        case Left(_)  => None
      }
    case None => None
  }

  def listSshKeys(account: Account): List[SshKey] = r.clients.withClient { client =>
    client.zrange(account.sshKeysListId) match {
      case Some(l: List[String]) => l.flatMap(id => getSshKey(id, account, client))
      case None                  => List()
    }
  }

  /**
   * Number of Ssh keys on Account
   * */
  def cardinalitySshKey(account: Account): Either[RzError, Long] = r.clients.withClient { client =>
    client.zcard(account.sshKeysListId) match {
      case Some(s: Long) => Right(s)
      case None          => Left(NotFoundInRepository)
    }
  }
}
