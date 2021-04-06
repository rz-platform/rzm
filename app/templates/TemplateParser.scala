package templates

import models.Template
import play.api.libs.json.{ JsValue, Json }
import repositories.{ FileNotFound, JsonParseError, RzError }

import java.io.File
import java.nio.file.Paths
import scala.io.Source
import scala.util.{ Failure, Success, Try, Using }

object TemplateParser {
  def parse(path: File): Template = {
    val name         = path.getName.split('-').map(_.capitalize).mkString(" ")
    val absolutePath = path.getAbsolutePath
    val description: List[String] = readTextFile(Paths.get(absolutePath, "readme.txt").toFile) match {
      case Success(l) => l
      case Failure(_) => List()
    }
    val js = readJson(absolutePath)
    val fields = js match {
      case Right(v) => TemplateFieldParser.parseFields(v)
      case Left(_)  => List()
    }
    val entrypoint = js match {
      case Right(v) => TemplateFieldParser.parseEntrypoint(v)
      case Left(_)  => None
    }
    val illustration = illustrationFileName(entrypoint)

    Template(name, description, path, entrypoint, illustration, fields)
  }

  private def illustrationFileName(entrypoint: Option[String]): Option[String] =
    entrypoint match {
      case Some(e) => Some(s"${e.replaceFirst("[.][^.]+$", "")}.pdf")
      case _       => None
    }

  private def readJson(absolutePath: String): Either[RzError, JsValue] =
    readTextFile(Paths.get(absolutePath, "schema.json").toFile) match {
      case Success(l) =>
        Try(Json.parse(l.mkString)) match {
          case Success(v) => Right(v)
          case Failure(_) => Left(JsonParseError)
        }
      case Failure(_) => Left(FileNotFound)
    }

  private def readTextFile(filename: File): Try[List[String]] =
    Using(Source.fromFile(filename))(source => (for (line <- source.getLines) yield line).toList)

}
