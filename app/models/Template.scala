package models

import play.api.libs.functional.syntax._
import play.api.libs.json._

import java.io.File

case class Template(
  id: String,
  description: List[String],
  path: File,
  entrypoint: Option[String],
  texCompiler: Option[String],
  bibCompiler: Option[String],
  fields: List[Field]
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

sealed trait Field {
  def name: String
  def label: String
  def description: Option[String]
}

case class Numeric(name: String, label: String, description: Option[String], min: Int, max: Int, default: Int)
    extends Field
object Numeric {
  val t = "numeric"

  implicit val reads: Reads[Numeric] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "label").read[String] and
      (JsPath \ "description").readNullable[String] and
      (JsPath \ "min").read[Int] and
      (JsPath \ "max").read[Int] and
      (JsPath \ "default").read[Int]
  )(Numeric.apply _)
}

case class Choice(name: String, label: String, description: Option[String], choices: List[String], default: String)
    extends Field
object Choice {
  val t = "select"

  implicit val reads: Reads[Choice] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "label").read[String] and
      (JsPath \ "description").readNullable[String] and
      (JsPath \ "choices").read[List[String]] and
      (JsPath \ "default").read[String]
  )(Choice.apply _)
}

case class Checkbox(name: String, label: String, description: Option[String], default: Boolean) extends Field
object Checkbox {
  val t = "checkbox"

  implicit val reads: Reads[Checkbox] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "label").read[String] and
      (JsPath \ "description").readNullable[String] and
      (JsPath \ "default").read[Boolean]
  )(Checkbox.apply _)
}
