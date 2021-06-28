package templates.models

import documents.models.FilePath

import java.io.File

case class Template(
  id: String,
  description: List[String],
  path: File,
  entrypoint: Option[String],
  texCompiler: Option[String],
  bibCompiler: Option[String],
  fields: List[RzField]
) {
  val name: String = id.split('-').map(_.capitalize).mkString(" ")

  def files: Array[File] =
    FilePath
      .recursiveList(path)
      .filter(Template.filter)

  private val illustration: Option[String] =
    entrypoint match {
      case Some(e) => Some(s"${e.replaceFirst("[.][^.]+$", "")}.pdf")
      case _       => None
    }

  val illustrationFile: Option[File] = illustration match {
    case Some(s) => Template.readFile(path.toString + "/" + s)
    case _       => None
  }

  def this(name: String, description: List[String], path: File) =
    this(name, description, path, None, None, None, List())
}

object Template {
  val excludedExt = List("pdf")
  val excluded    = List("schema.json")

  def readFile(path: String): Option[File] = {
    val file = new java.io.File(path)
    if (file.exists()) {
      Some(file)
    } else {
      None
    }
  }

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
