package users.models

import infrastructure.models.{ PersistentEntity, PersistentEntityMap, PersistentEntityString, RedisKeyPrefix }
import infrastructure.repositories.RzDateTime
import play.api.libs.json.{ Format, Json }

case class Account(
  id: String,
  username: String,
  fullname: String,
  email: String,
  tz: String,
  created: Long,
  picture: Option[String]
) extends PersistentEntityMap {
  val prefix: String = RedisKeyPrefix.accountPrefix

  def toMap: Map[String, String] = {
    // take advantage of the iterable nature of Option
    val picture = if (this.picture.nonEmpty) Some("picture" -> this.picture.get) else None
    (Seq("username" -> username, "fullname" -> fullname, "email" -> email, "created" -> created.toString, "tz" -> tz) ++ picture).toMap
  }

  def this(data: UserRegistration) =
    this(
      PersistentEntity.id,
      data.username,
      data.fullName.getOrElse(""),
      data.email,
      data.timezone,
      RzDateTime.now,
      None
    )

  def fromForm(id: String, data: UserUpdate): Account =
    Account(id, username, data.fullName.getOrElse(""), data.email, tz, created, picture)
}

object Account {
  def key(id: String): String = PersistentEntity.key(RedisKeyPrefix.accountPrefix, id)

  def make(id: String, data: Map[String, String]): Option[Account] =
    for {
      username <- data.get("username")
      fullname <- data.get("fullname")
      email    <- data.get("email")
      created  <- data.get("created")
      tz       <- data.get("tz")
    } yield Account(
      id,
      username,
      fullname,
      email,
      tz,
      RzDateTime.parseTimestamp(created),
      data.get("picture")
    )
}

object AccountUsername {
  def asEntity(account: Account) = PersistentEntityString(RedisKeyPrefix.accountUsername, account.username, account.id)

  def asKey(username: String) = PersistentEntity.key(RedisKeyPrefix.accountUsername, username)
}

object AccountEmail {
  def asEntity(account: Account) = PersistentEntityString(RedisKeyPrefix.userEmailId, account.email, account.id)

  def asKey(email: String) = PersistentEntity.key(RedisKeyPrefix.userEmailId, email)
}

object AccountPassword {
  def asEntity(account: Account, passwordHash: String) =
    PersistentEntityString(RedisKeyPrefix.accountPasswordPrefix, account.id, passwordHash)

  def asKey(account: Account) = PersistentEntity.key(RedisKeyPrefix.accountPasswordPrefix, account.id)
}

object AccountSshKeys {
  def key(account: Account) = PersistentEntity.key(RedisKeyPrefix.accountSshPrefix, account.id)
}

object AccountProjects {
  def key(account: Account) = PersistentEntity.key(RedisKeyPrefix.accountAccessListPrefix, account.id)
}

case class AccountInfo(id: String)

object AccountInfo {
  // Use a JSON format to automatically convert between case class and JsObject
  implicit val format: Format[AccountInfo] = Json.format[AccountInfo]
}
