package models

import java.nio.ByteBuffer
import java.util.{Base64, UUID}

trait PersistentEntity {
  def id: String
  def key: String

  def prefix: String = PersistentEntity.prefix(key)
}

object PersistentEntity {
  /*
  * UUIDs are long – textual representation that’s 36 characters
  * taking the binary representation of the UUID, and using Base64 encoding,
  * you get a textual version that’s only 22 characters long,
  * whilst still being able to read the resulting string
  *
  * Result: 22 characters, URL and Filename Safe
   */
  def id: String = {
    val uuid       = UUID.randomUUID()
    val byteBuffer = ByteBuffer.allocate(16)
    byteBuffer.putLong(uuid.getMostSignificantBits)
    byteBuffer.putLong(uuid.getLeastSignificantBits)

    Base64.getEncoder
      .withoutPadding()
      .encodeToString(byteBuffer.array())
      .replaceAll("/", "-")
      .replaceAll("\\+", "_")
  }

  def prefix(strings: String*): String = strings.mkString(":")
}

object RedisKeyPrefix {
  //  Always use two-letters

  val accountPrefix           = "ai:" // account instance
  val accountPasswordPrefix   = "ap:" // account password
  val accountSshPrefix        = "as:" // account ssh prefix
  val accountAccessListPrefix = "al:" // account access list
  val userEmailId             = "ae:" // account email

  val rzRepoPrefix              = "ri:" // repository instance
  val rzRepoConfPrefix          = "rg:" // repository configuration
  val lastOpenedFilePrefix      = "rl:" // repository last opened file for user
  val rzRepoCollaboratorsPrefix = "rc:" // repository collaborators

  val collaboratorPrefix = "ci:" // collaborator instance

  val sshKeyPrefix = "sk:" // ssh key
}
