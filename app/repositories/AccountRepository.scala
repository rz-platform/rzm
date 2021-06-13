package repositories

import com.redis.RedisClient
import infrastructure.Redis
import models._

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class AccountRepository @Inject() (redis: Redis)(implicit ec: ExecutionContext) {
  private val logger = play.api.Logger(this.getClass)

  def getById(id: String, client: RedisClient): Either[RzError, Account] =
    client.hgetall[String, String](Account.key(id)) match {
      case Some(account) => Account.make(id, account).toRight(ParsingError)
      case None          => Left(NotFoundInRepository)
    }

  def getById(id: String): Future[Either[RzError, Account]] = Future {
    redis.withClient(client => getById(id, client))
  }

  def getById(id: Option[String], client: RedisClient): Either[RzError, Account] = id match {
    case Some(id) => getById(id, client)
    case _        => Left(NotFoundInRepository)
  }

  def getByName(username: String, client: RedisClient): Either[RzError, Account] =
    client.get(AccountUsername.asKey(username)) match {
      case Some(id: String) => getById(id, client)
      case None             => Left(NotFoundInRepository)
    }

  def getByName(username: String): Future[Either[RzError, Account]] = Future {
    redis.withClient(client => getByName(username, client))
  }

  def getByEmail(email: String): Future[Either[RzError, Account]] = Future {
    redis.withClient { client =>
      client.get(AccountEmail.asKey(email)) match {
        case Some(id: String) => getById(id, client)
        case None             => Left(NotFoundInRepository)
      }
    }
  }

  def getByUsernameOrEmail(s: String): Future[Either[RzError, Account]] =
    getByName(s).flatMap {
      case Right(account) => Future(Right(account))
      case _              => getByEmail(s)
    }

  def set(
    account: Account,
    username: PersistentEntityString,
    email: PersistentEntityString,
    password: PersistentEntityString
  ): Future[_] = Future {
    redis.withClient(client =>
      client.pipeline { f =>
        f.hmset(account.key, account.toMap)
        f.set(email.key, email.value)
        f.set(username.key, username.value)
        f.set(password.key, password.value)
      }
    )
  }

  def setTimezone(account: Account, tz: String): Future[_] = Future {
    redis.withClient(client => client.hset(account.key, "tz", tz))
  }

  def update(oldAccount: Account, account: Account): Future[_] = Future {
    redis.withClient { client =>
      client.pipeline { f =>
        f.hmset(account.key, account.toMap)
        if (oldAccount.email != account.email) {
          f.del(AccountEmail.asKey(oldAccount.email))
          f.set(AccountEmail.asKey(account.email), account.key)
        }
        if (oldAccount.username != account.username) {
          f.del(AccountUsername.asKey(oldAccount.username))
          f.set(AccountUsername.asKey(account.username), account.key)
        }
      }
    }
  }

  def setPassword(password: PersistentEntityString): Future[Boolean] = Future {
    redis.withClient(client => client.set(password.key, password.value))
  }

  def getPassword(account: Account): Future[Either[RzError, String]] = Future {
    redis.withClient { client =>
      client.get(AccountPassword.asKey(account)) match {
        case Some(s) => Right(s)
        case None    => Left(NotFoundInRepository)
      }
    }
  }

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

  def removePicture(account: Account): Future[Option[Long]] = Future {
    redis.withClient(client => client.hdel(account.key, "picture"))
  }

  def setPicture(account: Account, filename: String): Future[Boolean] = Future {
    redis.withClient(client => client.hset(account.key, "picture", filename))
  }

  def getSshKey(id: String): Future[Option[SshKey]] = Future {
    redis.withClient { client =>
      client.hgetall(SshKey.key(id)) match {
        case Some(m) if m.contains("owner") =>
          getById(m.get("owner"), client) match {
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
  def cardinalitySshKey(account: Account): Future[Either[RzError, Long]] = Future {
    redis.withClient { client =>
      client.zcard(AccountSshKeys.key(account)) match {
        case Some(s: Long) => Right(s)
        case None          => Left(NotFoundInRepository)
      }
    }
  }
}
