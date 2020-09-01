package models

import scala.util.matching.Regex

sealed trait Literal {
  def value: String
  override def toString: String = value
}

case object FileRoot extends Literal {
  override def value = "."
}

case object GitKeep extends Literal {
  override def value = ".gitkeep"
}

case object SessionName extends Literal {
  val value = "account_id"
}

case object ForbiddenSymbols {
  private val blockList: List[String]        = List("?", ":", "#", "/", "&", "..", "$", "%")
  def isNameValid(itemName: String): Boolean = blockList.exists(itemName contains _)

  override def toString: String = blockList.toString()
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
