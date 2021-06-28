package documents.models

import java.io.File
import java.nio.file.{ Path, Paths }

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
  def recursiveList(f: File): Array[File] = {
    val these = f.listFiles
    these ++ these.filter(_.isDirectory).flatMap(recursiveList)
  }

  def relativize(pathAbsolute: Path, path: String): String = {
    val pathBase: Path = Paths.get(path)
    val r              = pathAbsolute.relativize(pathBase).getParent
    if (r != null) {
      r.toString
    } else {
      "."
    }
  }

  def splitIntoComponents(path: String): Array[PathComponent] = {
    val split = RzPathUrl.make(path).uri.split("/")
    split.filter(x => x != ".").zipWithIndex.map {
      case (name, index) => PathComponent(name, split.slice(0, index + 1).mkString("/"))
    }
  }

  def extension(name: String): String = {
    val i = name.lastIndexOf('.')
    if (i > 0) { name.substring(i + 1) }
    else { "" }
  }
}
