package models

import scala.util.matching.Regex

trait Literal {
  def value: String

  override def toString: String = value
}

case object Auth {
  val SESSION_ID = "sessionId"

  val USER_INFO_COOKIE_NAME = "userInfo"
}

case object FileRoot extends Literal {
  override val value = "."
}

case object GitKeep extends Literal {
  override val value = ".gitkeep"
}

case object SessionName extends Literal {
  val value = "account_id"
}

case object ForbiddenSymbols {
  private val pathForbiddenSymbols: List[String]    = List("?", ":", "#", "&", "..", "$", "%")
  private val generalForbiddenSymbols: List[String] = pathForbiddenSymbols :+ "/"

  def isPathValid(itemName: String): Boolean = pathForbiddenSymbols.exists(itemName contains _)

  def isNameValid(itemName: String): Boolean = generalForbiddenSymbols.exists(itemName contains _)

  override def toString: String = generalForbiddenSymbols.mkString("") // for testing purposes
}

object ExcludedFileNames {
  val excluded: Array[String] = Array(GitKeep.toString, FileRoot.toString)

  def contains(name: String): Boolean = excluded.contains(name)
}

case object MaxDepthInFileTree {
  val toInt = 4
}

case object PublicKeyRegex {
  val toRegex: Regex =
    "^(ssh-rsa AAAAB3NzaC1yc2|ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNT|ssh-ed25519 AAAAC3NzaC1lZDI1NTE5|ssh-dss AAAAB3NzaC1kc3)[0-9A-Za-z+/]+[=]{0,3}( [^@]+@[^@]+)?$".r
}

case object AccountNameRegex {
  val toRegex: Regex = "^[A-Za-z\\d_\\-]+$".r
}

case object RepositoryNameRegex {
  val toRegex: Regex = "^[A-Za-z\\d_\\-]+$".r
}

sealed trait RepositoryPage

case object FileViewPage extends RepositoryPage

case object CollaboratorsPage extends RepositoryPage

case object CommitHistoryPage extends RepositoryPage

case object FileUploadPage extends RepositoryPage

object IdTable {
  /*
  Always use two-letters
   */

  val accountPrefix           = "ai:" // account instance
  val accountPasswordPrefix   = "ap:" // account password
  val accountSshPrefix        = "as:" // account password
  val accountAccessListPrefix = "al:" // account access list
  val userEmailId             = "ae:" // account email

  val rzRepoPrefix              = "ri:" // repository instance
  val rzRepoCollaboratorsPrefix = "rc:" // repository collaborators

  val collaboratorPrefix = "ci:" // collaborator instance

  val sshKeyPrefix = "sk:" // ssh key
}
