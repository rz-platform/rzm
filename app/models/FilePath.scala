package models

case class PathComponent(name: String, path: String)

case class FilePath(components: Array[PathComponent]) {
  def this(path: String) = this(FilePath.splitIntoComponents(path))

  val last: Option[String] = components.lastOption match {
    case Some(p) => Some(p.name)
    case _       => None
  }

  val path: Array[PathComponent] = components.dropRight(1)
}

object FilePath {
  def splitIntoComponents(path: String): Array[PathComponent] = {
    val split = RzPathUrl.make(path).uri.split("/")
    split.filter(x => x != ".").zipWithIndex.map {
      case (name, index) => PathComponent(name, split.slice(0, index + 1).mkString("/"))
    }
  }
}
