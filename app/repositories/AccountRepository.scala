package repositories

import anorm.SqlParser.get
import anorm._
import models.{ AccountData, RichAccount, SimpleAccount, SshKey }
import play.api.db.DBApi

import java.time.LocalDateTime
import javax.inject.{ Inject, Singleton }
import scala.concurrent.Future

@Singleton
class AccountRepository @Inject() (dbapi: DBApi)(implicit ec: DatabaseExecutionContext) {
  private val db     = dbapi.database("default")
  private val logger = play.api.Logger(this.getClass)

  val simpleAccountParser: RowParser[SimpleAccount] = {
    (get[Int]("account.id") ~ get[String]("account.username") ~ get[String]("account.email")
      ~ get[Boolean]("account.has_picture")).map {
      case id ~ userName ~ email ~ hasPicture => SimpleAccount(id, userName, email, hasPicture)
    }
  }

  private val richAccountParser = {
    (get[Int]("account.id") ~
      get[String]("account.username") ~
      get[String]("account.full_name") ~
      get[String]("account.email") ~
      get[String]("account.password") ~
      get[Boolean]("account.is_admin") ~
      get[LocalDateTime]("account.created_at") ~
      get[Boolean]("account.has_picture") ~
      get[String]("account.description")).map {
      case id ~ userName ~ fullName ~ email ~ password ~ isAdmin ~ registeredDate ~ hasPicture ~ description =>
        RichAccount(
          id,
          userName,
          fullName,
          email,
          password,
          isAdmin,
          registeredDate,
          hasPicture,
          description
        )
    }
  }

  val sshKeyParser: RowParser[SshKey] = {
    (get[Int]("ssh_key.id") ~ get[String]("ssh_key.public_key") ~ get[LocalDateTime]("ssh_key.created_at")).map {
      case id ~ publicKey ~ createdAt => SshKey(id, publicKey, createdAt)
    }
  }

  /**
   * Retrieve a user from the id.
   */
  def findById(id: Int): Future[Option[SimpleAccount]] =
    Future {
      db.withConnection { implicit connection =>
        SQL("select id, username, email, has_picture from account where id = {accountId}")
          .on("accountId" -> id)
          .as(simpleAccountParser.singleOpt)
      }
    }(ec)

  /**
   * Retrieve a simple account from login
   */
  def getByUsernameOrEmail(usernameOrEmail: String, email: String = ""): Future[Option[SimpleAccount]] =
    Future {
      db.withConnection { implicit connection =>
        SQL("select id, username, email, has_picture from account where username={usernameOrEmail} or email={email}")
          .on("usernameOrEmail" -> usernameOrEmail, "email" -> (if (email.isEmpty) usernameOrEmail else email))
          .as(simpleAccountParser.singleOpt)
      }
    }(ec)

  /**
   * Retrieve a rich account from login
   */
  def getRichModelByUsernameOrEmail(usernameOrEmail: String, email: String = ""): Future[Option[RichAccount]] =
    Future {
      db.withConnection { implicit connection =>
        SQL("select * from account where username={usernameOrEmail} or email={email}")
          .on("usernameOrEmail" -> usernameOrEmail, "email" -> (if (email.isEmpty) usernameOrEmail else email))
          .as(richAccountParser.singleOpt)
      }
    }(ec)

  /**
   * Retrieve a rich account from id
   */
  def getRichModelById(accountId: Int): Future[RichAccount] =
    Future {
      db.withConnection { implicit connection =>
        SQL(" select * from account where id={accountId}")
          .on("accountId" -> accountId)
          .as(richAccountParser.single)
      }
    }(ec)

  /**
   * Insert a new user
   *
   */
  def insert(account: RichAccount): Future[Option[Long]] =
    Future {
      db.withConnection { implicit connection =>
        SQL("""
        insert into account (username,full_name,email,password,is_admin, has_picture,description) values
          ({userName}, {fullName}, {email}, {password}, {isAdmin}, {hasPicture},  {description})
      """).bind(account).executeInsert()
      }
    }(ec)

  def updatePassword(id: Int, newPasswordHash: String): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL("""
          update account
          set password = {newPasswordHash}
          where account.id = {id}
      """).on("newPasswordHash" -> newPasswordHash, "id" -> id).executeUpdate()
      }
    }(ec)

  def updateProfileInfo(id: Int, accountData: AccountData): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL("""
          update account
          set full_name = {fullName},
          email = {email},
          description = {description}
          where account.id = {id}
      """).on(
            "fullName"    -> accountData.fullName.getOrElse(""),
            "email"       -> accountData.email,
            "description" -> accountData.description.getOrElse(""),
            "id"          -> id
          )
          .executeUpdate()
      }
    }(ec)

  def insertSshKey(accountId: Int, key: String): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL("insert into ssh_key (account_id, public_key) values ({accountId}, {publicKey})")
          .on("accountId" -> accountId, "publicKey" -> key)
          .executeUpdate()
      }
    }(ec)

  def numberOfAccountSshKeys(accountId: Int): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL("select count(id) as c from ssh_key where account_id = {accountId}")
          .on("accountId" -> accountId)
          .as(SqlParser.int("c").single)
      }
    }(ec)

  def accountSshKeys(accountId: Int): Future[List[SshKey]] =
    Future {
      db.withConnection { implicit connection =>
        SQL("select * from ssh_key where account_id = {accountId}")
          .on("accountId" -> accountId)
          .as(sshKeyParser.*)
      }
    }(ec)

  def deleteSshKeys(accountId: Int, keyId: Long): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL("delete from ssh_key where id = {keyId} and account_id={accountId}")
          .on("accountId" -> accountId, "keyId" -> keyId)
          .executeUpdate()
      }
    }(ec)

  def hasPicture(accountId: Int): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL("update account set has_picture = true where id = {accountId}")
          .on("accountId" -> accountId)
          .executeUpdate()
      }
    }(ec)

  def removePicture(accountId: Int): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL("update account set has_picture = false where id = {accountId}")
          .on("accountId" -> accountId)
          .executeUpdate()
      }
    }(ec)

}
