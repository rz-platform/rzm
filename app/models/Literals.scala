package models

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
  val value = "user_id"
}

case object ForbiddenSymbols extends Literal {
  override def value: String = "?:#/&"

  def toList: Array[String] = value.split("")
}

object ExcludedFileNames {
  val excluded: Array[String] = Array(GitKeep.toString, FileRoot.toString)

  def contains(name: String): Boolean = excluded.contains(name)
}

case object MaxDepthInFileTree {
  val toInt = 4
}
