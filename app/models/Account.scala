package models

import java.util.Calendar

import anorm._
import services.EncryptionService

import scala.collection.immutable.HashMap

sealed trait Account

case class SimpleAccount(id: Long, userName: String, email: String, hasPicture: Boolean) extends Account

object SimpleAccount {
  implicit def toParameters: ToParameterList[SimpleAccount] = Macro.toParameters[SimpleAccount]
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
) extends Account

object RichAccount {
  implicit def toParameters: ToParameterList[RichAccount] = Macro.toParameters[RichAccount]

  def buildNewAccount(userForm: AccountRegistrationData): RichAccount =
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

object AccessLevel {
  val owner   = 0
  val canEdit = 20
  val canView = 30

  val canEditName = "edit"
  val canViewName = "view"

  val map: HashMap[String, Int] = HashMap((canEditName, canEdit), (canViewName, canView))

  def fromString(accessLevel: String): Int =
    if (AccessLevel.map.contains(accessLevel)) AccessLevel.map(accessLevel)
    else AccessLevel.canView
}

case class AccountRegistrationData(userName: String, fullName: Option[String], password: String, email: String)

case class AccountData(userName: String, fullName: Option[String], email: String, description: Option[String])

case class PasswordData(oldPassword: String, newPassword: String)

case class AccountLoginData(userName: String, password: String)
