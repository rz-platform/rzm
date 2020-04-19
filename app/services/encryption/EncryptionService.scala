package services.encryption

import java.math.BigInteger
import java.security.MessageDigest

import org.mindrot.jbcrypt.BCrypt

object EncryptionService {
  def getHash(str: String): String = {
    BCrypt.hashpw(str, BCrypt.gensalt())
  }
  def checkHash(str: String, strHashed: String): Boolean = {
    BCrypt.checkpw(str, strHashed)
  }

  def md5HashString(s: String): String = {
    val md           = MessageDigest.getInstance("MD5")
    val digest       = md.digest(s.getBytes)
    val bigInt       = new BigInteger(1, digest)
    val hashedString = bigInt.toString(16)
    hashedString
  }
}
