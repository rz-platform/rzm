package models

import java.io.File

object Auth {
  val sessionId = "sessionId"

  val userInfoCookie = "userInfo"

  val sessionName = "account_id"
}

object FileNames {
  val root = "."
  val keep = ".gitkeep"
}

object ForbiddenSymbols {
  private val pathForbiddenSymbols: List[String]    = List("?", ":", "#", "&", "..", "$", "%")
  private val generalForbiddenSymbols: List[String] = pathForbiddenSymbols :+ "/"

  def isPathValid(itemName: String): Boolean = pathForbiddenSymbols.exists(itemName contains _)

  def isNameValid(itemName: String): Boolean = generalForbiddenSymbols.exists(itemName contains _)

  override def toString: String = generalForbiddenSymbols.mkString("") // for testing purposes
}

object TemplateExcluded {
  val excludedExt = List("pdf")
  val excluded    = List("schema.json")

  def filter(file: File): Boolean = {
    val name = file.getName
    val ext  = FilePath.extension(name)
    file match {
      case _ if file.isDirectory          => false
      case _ if excludedExt.contains(ext) => false
      case _ if excluded.contains(name)   => false
      case _                              => true
    }
  }
}

object RzRegex {
  val publicKey =
    "^(ssh-rsa AAAAB3NzaC1yc2|ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNT|ssh-ed25519 AAAAC3NzaC1lZDI1NTE5|ssh-dss AAAAB3NzaC1kc3)[0-9A-Za-z+/]+[=]{0,3}( [^@]+@[^@]+)?$".r

  val onlyAlphabet = "^[A-Za-z\\d_\\-]+$".r
}

sealed trait RepositoryPage
case object FileViewPage      extends RepositoryPage
case object CollaboratorsPage extends RepositoryPage
case object CommitHistoryPage extends RepositoryPage
case object FileUploadPage    extends RepositoryPage
case object NewFilePage       extends RepositoryPage
case object ConstructorPage   extends RepositoryPage

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
  val rzRepoConfPrefix          = "rg:" // repository configuration
  val lastOpenedFilePrefix      = "rl:" // repository last opened file for user
  val rzRepoCollaboratorsPrefix = "rc:" // repository collaborators

  val collaboratorPrefix = "ci:" // collaborator instance

  val sshKeyPrefix = "sk:" // ssh key
}
