package models

import repositories.{ ParsingError, RzError }

import java.security.MessageDigest
import java.util.Base64
import java.time.LocalDateTime

case class SshKey(
  publicKey: String,
  createdAt: Long,
  owner: Account
) {
  val id: String = IdTable.sshKeyPrefix + MD5.fromString(publicKey)

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

  lazy val createdAtDate: LocalDateTime = DateTime.fromTimeStamp(createdAt)

  def toMap = Map("createdAt" -> createdAt.toString, "owner" -> owner.userName, "key" -> publicKey)

  def this(publicKey: String, owner: Account) = this(publicKey, DateTime.now, owner)
}

object SshKey {
  def id(key: String): String = IdTable.sshKeyPrefix + MD5.fromString(key)

  def make(m: Map[String, String], account: Account): Either[RzError, SshKey] =
    (for {
      publicKey <- m.get("key")
      createdAt <- m.get("createdAt")
    } yield SshKey(publicKey, DateTime.parseTimestamp(createdAt), account)) match {
      case Some(m) => Right(m)
      case None    => Left(ParsingError)
    }
}
