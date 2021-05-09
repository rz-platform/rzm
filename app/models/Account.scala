package models

import play.api.libs.json.{ Format, Json }
import repositories.{ ParsingError, RzError }

case class Account(
  userName: String,
  fullName: String,
  email: String,
  tz: String,
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
    (Seq("fullName" -> fullName, "email" -> email, "created" -> created.toString, "tz" -> tz) ++ picture).toMap
  }

  def this(data: AccountRegistrationData) =
    this(
      data.userName,
      data.fullName.getOrElse(""),
      data.email,
      data.timezone,
      DateTime.now,
      None
    )

  def fromForm(data: AccountData): Account =
    Account(userName, data.fullName.getOrElse(""), data.email, tz, created, picture)
}

object Account {
  def id(username: String): String   = IdTable.accountPrefix + username
  def emailId(email: String): String = IdTable.userEmailId + email

  def make(key: String, data: Map[String, String]): Either[RzError, Account] =
    (for {
      fullName <- data.get("fullName")
      email    <- data.get("email")
      created  <- data.get("created")
      tz       <- data.get("tz")
    } yield Account(key.substring(3), fullName, email, tz, DateTime.parseTimestamp(created), data.get("picture"))) match {
      case Some(a) => Right(a)
      case None    => Left(ParsingError)
    }
}

case class UserInfo(username: String)

object UserInfo {
  // Use a JSON format to automatically convert between case class and JsObject
  implicit val format: Format[UserInfo] = Json.format[UserInfo]
}
