package authentication.models

import org.mindrot.jbcrypt.BCrypt

import java.math.BigInteger
import java.security.MessageDigest

case class Password(hash: String) {
  override def toString: String = hash

  def check(unencrypted: String): Boolean = BCrypt.checkpw(unencrypted, hash)
}

object Password {
  def fromString(unencryptedString: String): Password =
    Password(BCrypt.hashpw(unencryptedString, BCrypt.gensalt()))
}

object MD5 {
  def fromString(s: String): String = {
    val md           = MessageDigest.getInstance("MD5")
    val digest       = md.digest(s.getBytes)
    val bigInt       = new BigInteger(1, digest)
    val hashedString = bigInt.toString(16)
    hashedString
  }
}
