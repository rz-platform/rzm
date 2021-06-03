package models

import infrastructure.RzDateTime
import play.api.libs.json.{ Format, Json }

case class Account(
  id: String,
  username: String,
  fullname: String,
  email: String,
  tz: String,
  created: Long,
  picture: Option[String]
) extends PersistentEntity {
  val keyPrefix: String = RedisKeyPrefix.accountPrefix

  def key: String           = prefix(RedisKeyPrefix.accountPrefix, id)
  def emailKey: String      = prefix(RedisKeyPrefix.userEmailId, id)
  def passwordKey: String   = prefix(RedisKeyPrefix.accountPasswordPrefix, id)
  def sshKeysListKey: String = prefix(RedisKeyPrefix.accountSshPrefix, id)
  def projectListKey: String = prefix(RedisKeyPrefix.accountAccessListPrefix, id)

  def toMap: Map[String, String] = {
    // take advantage of the iterable nature of Option
    val picture = if (this.picture.nonEmpty) Some("picture" -> this.picture.get) else None
    (Seq("username" -> username, "fullname" -> fullname, "email" -> email, "created" -> created.toString, "tz" -> tz) ++ picture).toMap
  }

  def this(data: AccountRegistrationData) =
    this(
      PersistentEntity.id,
      data.userName,
      data.fullName.getOrElse(""),
      data.email,
      data.timezone,
      RzDateTime.now,
      None
    )

  def fromForm(id: String, data: AccountData): Account =
    Account(id, username, data.fullName.getOrElse(""), data.email, tz, created, picture)
}

object Account {
  def key(username: String): String   = s"${RedisKeyPrefix.accountPrefix}$username"
  def emailKey(email: String): String = s"${RedisKeyPrefix.userEmailId}$email"

  def make(key: String, data: Map[String, String]): Either[RzError, Account] =
    (for {
      username <- data.get("username")
      fullname <- data.get("fullName")
      email    <- data.get("email")
      created  <- data.get("created")
      tz       <- data.get("tz")
    } yield Account(
      key.substring(3),
      username,
      fullname,
      email,
      tz,
      RzDateTime.parseTimestamp(created),
      data.get("picture")
    )) match {
      case Some(a) => Right(a)
      case None    => Left(ParsingError)
    }
}

case class AccountInfo(username: String)

object AccountInfo {
  // Use a JSON format to automatically convert between case class and JsObject
  implicit val format: Format[AccountInfo] = Json.format[AccountInfo]
}
