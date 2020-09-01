package repositories

import java.util.Date

import anorm.SqlParser.get
import anorm._
import javax.inject.{ Inject, Singleton }
import models.{ AccountData, RichAccount, SimpleAccount, SshKey }
import play.api.db.DBApi

import scala.concurrent.Future

@Singleton
class AccountRepository @Inject() (dbapi: DBApi)(implicit ec: DatabaseExecutionContext) {
  private val db     = dbapi.database("default")
  private val logger = play.api.Logger(this.getClass)

  val simple: RowParser[SimpleAccount] = {
    (get[Long]("account.id") ~ get[String]("account.username") ~ get[String]("account.email")
      ~ get[Boolean]("account.has_picture")).map {
      case id ~ userName ~ email ~ hasPicture => SimpleAccount(id, userName, email, hasPicture)
    }
  }

  private val rich = {
    (get[Long]("account.id") ~
      get[String]("account.username") ~
      get[String]("account.full_name") ~
      get[String]("account.email") ~
      get[String]("account.password") ~
      get[Boolean]("account.is_admin") ~
      get[Date]("account.created_at") ~
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

  val sshKeysSimple: RowParser[SshKey] = {
    (get[Int]("ssh_key.id") ~ get[String]("ssh_key.public_key") ~ get[Date]("ssh_key.created_at")).map {
      case id ~ publicKey ~ createdAt => SshKey(id, publicKey, createdAt)
    }
  }

  /**
   * Retrieve a user from the id.
   */
  def findById(id: Long): Future[Option[SimpleAccount]] =
    Future {
      db.withConnection { implicit connection =>
        SQL"select id, username, email, has_picture from account where id = $id".as(simple.singleOpt)
      }
    }(ec)

  /**
   * Retrieve a simple account from login
   */
  def getByUsernameOrEmail(usernameOrEmail: String, email: String = ""): Future[Option[SimpleAccount]] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""
             select id, username, email, has_picture
             from account where userName= {usernameOrEmail} or email ={email}""".stripMargin)
          .on("usernameOrEmail" -> usernameOrEmail, "email" -> (if (email.isEmpty) usernameOrEmail else email))
          .as(simple.singleOpt)
      }
    }(ec)

  /**
   * Retrieve a rich account from login
   */
  def getRichModelByLoginOrEmail(usernameOrEmail: String, email: String = ""): Future[Option[RichAccount]] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""
             select *
             from account where userName= {usernameOrEmail} or email ={email}""".stripMargin)
          .on("usernameOrEmail" -> usernameOrEmail, "email" -> (if (email.isEmpty) usernameOrEmail else email))
          .as(rich.singleOpt)
      }
    }(ec)

  /**
   * Retrieve a rich account from id
   */
  def getRichModelById(accountId: Long): Future[RichAccount] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""
             select *
             from account where id=$accountId
             """)
          .as(rich.single)
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
        insert into account (userName,full_name,email,password,is_admin, has_picture,description) values (
          {userName}, {fullName}, {email}, {password}, {isAdmin}, {hasPicture},  {description}
        )
      """).bind(account).executeInsert()
      }
    }(ec)

  def updatePassword(id: Long, newPasswordHash: String): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""
          UPDATE account
          SET password = {newPasswordHash}
          WHERE account.id = {id}
      """).on("newPasswordHash" -> newPasswordHash, "id" -> id).executeUpdate()
      }
    }(ec)

  def updateProfileInfo(id: Long, accountData: AccountData): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""
          UPDATE account
          SET full_name = {fullName},
          email = {email},
          description = {description}
          WHERE account.id = {id}
      """).on(
            "fullName"    -> accountData.fullName.getOrElse(""),
            "email"       -> accountData.email,
            "description" -> accountData.description.getOrElse(""),
            "id"          -> id
          )
          .executeUpdate()
      }
    }(ec)

  def insertSshKey(accountId: Long, key: String): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""
             insert into ssh_key (account_id, public_key) values ({accountId}, {publicKey})
             """)
          .on("accountId" -> accountId, "publicKey" -> key)
          .executeUpdate()
      }
    }(ec)

  def numberOfAccountSshKeys(accountId: Long): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""select count(id) as c from ssh_key where account_id = {accountId}""")
          .on("accountId" -> accountId)
          .as(SqlParser.int("c").single)
      }
    }(ec)

  def accountSshKeys(accountId: Long): Future[List[SshKey]] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""select * from ssh_key where account_id = {accountId}""")
          .on("accountId" -> accountId)
          .as(sshKeysSimple.*)
      }
    }(ec)

  def deleteSshKeys(accountId: Long, keyId: Long): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""delete from ssh_key where id = {keyId} and account_id={accountId}""")
          .on("accountId" -> accountId, "keyId" -> keyId)
          .executeUpdate()
      }
    }(ec)

  def hasPicture(accountId: Long): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""
          UPDATE account
          SET has_picture = true
          WHERE id = {accountId}
      """).on("accountId" -> accountId)
          .executeUpdate()
      }
    }(ec)

  def removePicture(accountId: Long): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""
          UPDATE account
          SET has_picture = false
          WHERE id = {accountId}
      """).on("accountId" -> accountId).executeUpdate()
      }
    }(ec)

}
