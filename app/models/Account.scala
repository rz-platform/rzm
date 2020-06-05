package models

import java.util.Calendar

import anorm._

import scala.collection.immutable.HashMap

sealed trait Account {
  def id: Long
  def userName: String
  def email: String
}

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

  def fromScratch(userForm: AccountRegistrationData): RichAccount =
    RichAccount(
      0,
      userForm.userName.trim.toLowerCase,
      userForm.fullName.getOrElse(""),
      userForm.email.trim.toLowerCase,
      HashedString.fromString(userForm.password).toString,
      created = Calendar.getInstance().getTime,
      hasPicture = false
    )
}

case class AccountRegistrationData(userName: String, fullName: Option[String], password: String, email: String)

case class AccountData(userName: String, fullName: Option[String], email: String, description: Option[String])

case class PasswordData(oldPassword: String, newPassword: String)

case class AccountLoginData(userName: String, password: String)
