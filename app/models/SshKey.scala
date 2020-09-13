package models

import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.Base64

import anorm.{ Macro, ToParameterList }

case class SshKey(
  id: Int = 0,
  publicKey: String,
  createdAt: LocalDateTime
) {
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
}
object SshKey {
  implicit def toParameters: ToParameterList[SshKey] = Macro.toParameters[SshKey]
}

case class SshKeyData(publicKey: String)
case class SshRemoveData(id: Int)
