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
    val id           = path.getName.toLowerCase.replace(" ", "-")
    val absolutePath = path.getAbsolutePath
    val description: List[String] =
      readTextFile(Paths.get(absolutePath, "readme.txt").toFile) match {
        case Success(l) => l
        case Failure(_) => List()
      }
    readJson(absolutePath) match {
      case Right(v) =>
        val fields      = TemplateFieldParser.parseFields(v)
        val entrypoint  = TemplateFieldParser.parseArg(v, "entrypoint")
        val texCompiler = TemplateFieldParser.parseArg(v, "compiler")
        val bibCompiler = TemplateFieldParser.parseArg(v, "bib")
        Template(id, description, path, entrypoint, texCompiler, bibCompiler, fields)
      case Left(_) => new Template(id, description, path)
    }
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
    Using(Source.fromFile(filename))(source => (for (line <- source.getLines()) yield line).toList)

}
