package models

import java.time.LocalDateTime

import anorm._

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
  created: LocalDateTime,
  hasPicture: Boolean,
  description: String = ""
) extends Account

object RichAccount {
  implicit def toParameters: ToParameterList[RichAccount] = Macro.toParameters[RichAccount]

  def fromScratch(userForm: AccountRegistrationData): RichAccount =
    RichAccount(
      0,
      userForm.userName,
      userForm.fullName.getOrElse(""),
      userForm.email,
      HashedString.fromString(userForm.password).toString,
      created = LocalDateTime.now(),
      hasPicture = false
    )
}

case class AccountRegistrationData(userName: String, fullName: Option[String], password: String, email: String)

case class AccountData(userName: String, fullName: Option[String], email: String, description: Option[String])

case class PasswordData(oldPassword: String, newPassword: String)

case class AccountLoginData(userName: String, password: String)
