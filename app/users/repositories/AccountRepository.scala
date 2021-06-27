package users.repositories

import com.redis.RedisClient
import infrastructure.errors.{ NotFoundInRepository, ParsingError, RepositoryError }
import infrastructure.models.PersistentEntityString
import infrastructure.repositories.Redis
import users.models._

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class AccountRepository @Inject() (redis: Redis)(implicit ec: ExecutionContext) {
  private val logger = play.api.Logger(this.getClass)

  def getById(id: String, client: RedisClient): Either[RepositoryError, Account] =
    client.hgetall[String, String](Account.key(id)) match {
      case Some(account) => Account.make(id, account).toRight(ParsingError)
      case None          => Left(NotFoundInRepository)
    }

  def getById(id: String): Future[Either[RepositoryError, Account]] = Future {
    redis.withClient(client => getById(id, client))
  }

  def getById(id: Option[String], client: RedisClient): Either[RepositoryError, Account] = id match {
    case Some(id) => getById(id, client)
    case _        => Left(NotFoundInRepository)
  }

  def getByName(username: String, client: RedisClient): Either[RepositoryError, Account] =
    client.get(AccountUsername.asKey(username)) match {
      case Some(id: String) => getById(id, client)
      case None             => Left(NotFoundInRepository)
    }

  def getByName(username: String): Future[Either[RepositoryError, Account]] = Future {
    redis.withClient(client => getByName(username, client))
  }

  def getByEmail(email: String): Future[Either[RepositoryError, Account]] = Future {
    redis.withClient { client =>
      client.get(AccountEmail.asKey(email)) match {
        case Some(id: String) => getById(id, client)
        case None             => Left(NotFoundInRepository)
      }
    }
  }

  def getByUsernameOrEmail(s: String): Future[Either[RepositoryError, Account]] =
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

  def getPassword(account: Account): Future[Either[RepositoryError, String]] = Future {
    redis.withClient { client =>
      client.get(AccountPassword.asKey(account)) match {
        case Some(s) => Right(s)
        case None    => Left(NotFoundInRepository)
      }
    }
  }

  def removePicture(account: Account): Future[Option[Long]] = Future {
    redis.withClient(client => client.hdel(account.key, "picture"))
  }

  def setPicture(account: Account, filename: String): Future[Boolean] = Future {
    redis.withClient(client => client.hset(account.key, "picture", filename))
  }

}
