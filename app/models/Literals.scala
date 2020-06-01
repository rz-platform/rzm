package models

trait Literal {
  def value: String
  override def toString: String = value
}

case class FileRoot() extends Literal {
  override def value = "."
}

case class GitKeep() extends Literal {
  override def value = ".gitkeep"
}

case class ForbiddenSymbols() extends Literal {
  override def value: String = "?:#/&"

  def toList: Array[String] = value.split("")
}

object ExcludedFileNames {
  val excluded: Array[String] = Array(GitKeep().toString, FileRoot().toString)

  def contains(name: String): Boolean = excluded.contains(name)
}
