package models

import infrastructure.RzDateTime

import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.Base64
case class SshKey(
  publicKey: String,
  createdAt: Long,
  owner: Account
) extends PersistentEntityMap {
  val prefix     = RedisKeyPrefix.sshKeyPrefix
  val id: String = MD5.fromString(publicKey)

  lazy val fingerprint: String = {
    val derFormat            = publicKey.split(" ")(1).trim
    val messageDigest        = MessageDigest.getInstance("MD5")
    val digest               = messageDigest.digest(Base64.getDecoder.decode(derFormat))
    val toRet: StringBuilder = new StringBuilder()
    var i                    = 0
    while ({
      i < digest.length
    }) {
      if (i != 0) toRet.append(":")
      val b   = digest(i) & 0xff
      val hex = Integer.toHexString(b)
      if (hex.length == 1) toRet.append("0")
      toRet.append(hex)

      i += 1
    }
    toRet.toString()
  }

  lazy val email: String = publicKey.split(" ")(2).trim

  lazy val createdAtDate: LocalDateTime = RzDateTime.fromTimestamp(createdAt)

  def toMap = Map("createdAt" -> createdAt.toString, "owner" -> owner.username, "key" -> publicKey)

  def this(publicKey: String, owner: Account) = this(publicKey, RzDateTime.now, owner)
}

object SshKey {
  def id(key: String): String = PersistentEntity.key(RedisKeyPrefix.sshKeyPrefix, MD5.fromString(key))

  def make(m: Map[String, String], account: Account): Either[RzError, SshKey] =
    (for {
      publicKey <- m.get("key")
      createdAt <- m.get("createdAt")
    } yield SshKey(publicKey, RzDateTime.parseTimestamp(createdAt), account)) match {
      case Some(m) => Right(m)
      case None    => Left(ParsingError)
    }
}
