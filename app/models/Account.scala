package models

import repositories.{ ParsingError, RzError }

case class Account(
  userName: String,
  fullName: String,
  email: String,
  created: Long,
  picture: Option[String]
) {
  def id: String            = IdTable.accountPrefix + userName
  def emailId: String       = IdTable.userEmailId + email
  def passwordId: String    = IdTable.accountPasswordPrefix + userName
  def sshKeysListId: String = IdTable.accountSshPrefix + userName
  def projectListId: String = IdTable.accountAccessListPrefix + userName

  // username is the key
  def toMap =
    Map(
      "fullName" -> fullName,
      "email"    -> email,
      "created"  -> created.toString,
      "picture"  -> picture
    )

  def this(userForm: AccountRegistrationData) =
    this(
      userForm.userName,
      userForm.fullName.getOrElse(""),
      userForm.email,
      DateTime.now,
      None
    )
}

object Account {
  def id(username: String): String   = IdTable.accountPrefix + username
  def emailId(email: String): String = IdTable.userEmailId + email

  def make(key: String, data: Map[String, String]): Either[RzError, Account] = {
    val a = for {
      fullName <- data.get("fullName")
      email    <- data.get("email")
      created  <- data.get("created")
    } yield Account(key.substring(3), fullName, email, DateTime.parseTimestamp(created), data.get("picture"))
    a match {
      case Some(a) => Right(a)
      case None    => Left(ParsingError)
    }
  }
}
