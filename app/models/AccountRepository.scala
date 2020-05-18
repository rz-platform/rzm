package models

import java.util.{ Calendar, Date }

import anorm.SqlParser.get
import anorm._
import javax.inject.{ Inject, Singleton }
import play.api.db.DBApi
import services.encryption.EncryptionService

import scala.collection.immutable.HashMap
import scala.concurrent.Future

case class Account(id: Long, userName: String, email: String, hasPicture: Boolean)

object Account {
  implicit def toParameters: ToParameterList[Account] = Macro.toParameters[Account]
}

case class RichAccount(
    id: Long,
    userName: String,
    fullName: String = "",
    email: String,
    password: String,
    isAdmin: Boolean = false,
    created: java.util.Date,
    hasPicture: Boolean,
    description: String = ""
)

object RichAccount {
  implicit def toParameters: ToParameterList[RichAccount] = Macro.toParameters[RichAccount]

  def buildNewAccount(userForm: AccountRegistrationData): RichAccount = {
    RichAccount(
      0,
      userForm.userName.trim.toLowerCase,
      userForm.fullName.getOrElse(""),
      userForm.email.trim.toLowerCase,
      EncryptionService.getHash(userForm.password),
      created = Calendar.getInstance().getTime,
      hasPicture = false
    )
  }
}

object AccessLevel {
  val owner   = 0
  val canEdit = 20
  val canView = 30

  val canEditName = "edit"
  val canViewName = "view"

  val map: HashMap[String, Int] = HashMap((canEditName, canEdit), (canViewName, canView))

  def fromString(accessLevel: String): Int = {
    if (AccessLevel.map.contains(accessLevel)) AccessLevel.map(accessLevel)
    else AccessLevel.canView
  }
}

case class AccountRegistrationData(userName: String, fullName: Option[String], password: String, email: String)

case class AccountData(userName: String, fullName: Option[String], email: String, description: Option[String])

case class PasswordData(oldPassword: String, newPassword: String)

case class AccountLoginData(userName: String, password: String)

@Singleton
class AccountRepository @Inject() (dbapi: DBApi)(implicit ec: DatabaseExecutionContext) {
  private val db     = dbapi.database("default")
  private val logger = play.api.Logger(this.getClass)

  private[models] val simple = {
    (get[Long]("account.id") ~ get[String]("account.username") ~ get[String]("account.email")
      ~ get[Boolean]("account.has_picture")).map {
      case id ~ userName ~ email ~ hasPicture => Account(id, userName, email, hasPicture)
    }
  }

  private[models] val rich = {
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

  /**
   * Retrieve a user from the id.
   */
  def findById(id: Long): Future[Option[Account]] =
    Future {
      db.withConnection { implicit connection =>
        SQL"select id, username, email, has_picture from account where id = $id".as(simple.singleOpt)
      }
    }(ec)

  /**
   * Retrieve a simple account from login
   */
  def getByLoginOrEmail(usernameOrEmail: String, email: String = ""): Future[Option[Account]] =
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

  def hasPicture(id: Long): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""
          UPDATE account
          SET has_picture = true
          WHERE id = ${id}
      """).executeUpdate()
      }
    }(ec)

  def removePicture(id: Long): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""
          UPDATE account
          SET has_picture = false
          WHERE id = ${id}
      """).executeUpdate()
      }
    }(ec)

}
