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

  def toMap: Map[String, String] = {
    // take advantage of the iterable nature of Option
    val picture = if (this.picture.nonEmpty) Some("picture" -> this.picture.get) else None
    (Seq("fullName" -> fullName, "email" -> email, "created" -> created.toString) ++ picture).toMap
  }

  def this(userForm: AccountRegistrationData) =
    this(
      userForm.userName,
      userForm.fullName.getOrElse(""),
      userForm.email,
      DateTime.now,
      None
    )

  def fromForm(userForm: AccountData): Account =
    Account(userName, userForm.fullName.getOrElse(""), userForm.email, created, picture)
}

object Account {
  def id(username: String): String   = IdTable.accountPrefix + username
  def emailId(email: String): String = IdTable.userEmailId + email

  def make(key: String, data: Map[String, String]): Either[RzError, Account] =
    (for {
      fullName <- data.get("fullName")
      email    <- data.get("email")
      created  <- data.get("created")
    } yield Account(key.substring(3), fullName, email, DateTime.parseTimestamp(created), data.get("picture"))) match {
      case Some(a) => Right(a)
      case None    => Left(ParsingError)
    }
}
