package models

import java.util.{Calendar, Date}

import anorm.SqlParser.get
import anorm._
import javax.inject.{Inject, Singleton}
import play.api.db.DBApi
import services.encryption.EncryptionService

import scala.collection.immutable.HashMap
import scala.concurrent.Future

case class Account(
  id: Long,
  userName: String,
  fullName: String = "",
  mailAddress: String,
  password: String,
  isAdmin: Boolean = false,
  registeredDate: java.util.Date,
  image: String = "",
  isRemoved: Boolean = false,
  description: String = ""
)

object Account {
  implicit def toParameters: ToParameterList[Account] =
    Macro.toParameters[Account]

  def buildNewAccount(userForm: AccountRegistrationData): Account = {
    Account(
      0,
      userForm.userName,
      userForm.fullName.getOrElse(""),
      userForm.mailAddress,
      EncryptionService.getHash(userForm.password),
      registeredDate = Calendar.getInstance().getTime
    )
  }
}

object AccessLevel {
  val owner = 0
  val canEdit = 20
  val canView = 30

  val canEditName = "edit"
  val canViewName = "view"

  val map: HashMap[String, Int] = HashMap((canEditName, canEdit), (canViewName, canView))
}

case class AccountRegistrationData(userName: String, fullName: Option[String], password: String, mailAddress: String)

case class AccountData(userName: String, fullName: Option[String], mailAddress: String, description: Option[String])

case class PasswordData(oldPassword: String, newPassword: String)

case class AccountLoginData(userName: String, password: String)

@Singleton
class AccountRepository @Inject() (dbapi: DBApi)(implicit ec: DatabaseExecutionContext) {
  private val db = dbapi.database("default")

  /**
   * Parse a Computer from a ResultSet
   */
  private[models] val simple = {
    get[Long]("account.id") ~
      get[String]("account.userName") ~
      get[String]("account.fullName") ~
      get[String]("account.mailAddress") ~
      get[String]("account.password") ~
      get[Boolean]("account.isAdmin") ~
      get[Date]("account.registeredDate") ~
      get[String]("account.image") ~
      get[Boolean]("account.isRemoved") ~
      get[String]("account.description") map {
      case id ~ userName ~ fullName ~ mailAddress ~ password ~ isAdmin ~ registeredDate ~ image ~ isRemoved ~ description =>
        Account(id, userName, fullName, mailAddress, password, isAdmin, registeredDate, image, isRemoved, description)
    }
  }
  private val logger = play.api.Logger(this.getClass)

  /**
   * Retrieve a user from the id.
   */
  def findById(id: Long): Future[Option[Account]] =
    Future {
      db.withConnection { implicit connection =>
        SQL"select * from account where id = $id".as(simple.singleOpt)
      }
    }(ec)

  /**
   * Retrieve a user from login
   */
  def findByLoginOrEmail(usernameOrEmail: String, email: String = ""): Future[Option[Account]] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""select * from account where userName= {usernameOrEmail} or mailAddress ={email}""")
          .on("usernameOrEmail" -> usernameOrEmail, "email" -> (if (email.isEmpty) usernameOrEmail else email))
          .as(simple.singleOpt)
      }
    }(ec)

  /**
   * Insert a new user
   *
   */
  def insert(account: Account): Future[Option[Long]] =
    Future {
      db.withConnection { implicit connection =>
        SQL("""
        insert into account (userName,fullName,mailAddress,password,isAdmin,registeredDate,image,isRemoved,description) values (
          {userName}, {fullName}, {mailAddress}, {password}, {isAdmin}, {registeredDate}, {image}, {isRemoved}, {description}
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
          SET fullName = {fullName},
          mailAddress = {mailAddress},
          description = {description}
          WHERE account.id = {id}
      """).on("fullName" -> accountData.fullName.getOrElse(""),
              "mailAddress" -> accountData.mailAddress,
              "description" -> accountData.description.getOrElse(""),
              "id" -> id)
          .executeUpdate()
      }
    }(ec)

}
