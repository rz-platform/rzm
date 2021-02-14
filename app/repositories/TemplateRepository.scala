package repositories

import models._
import play.api.Configuration
import play.api.libs.json._

import java.io.File
import java.nio.file.Paths
import javax.inject.{ Inject, Singleton }
import scala.io.Source
import scala.util.{ Failure, Success, Try, Using }

@Singleton
class TemplateRepository @Inject() (config: Configuration) {
  private val logger = play.api.Logger(this.getClass)

  private val templatesDir = new File(config.get[String]("play.server.templates.dir"))

  private def readTxt(filename: File): Try[List[String]] =
    Using(Source.fromFile(filename))(source => (for (line <- source.getLines) yield line).toList)

  private def parseJson(text: String): Try[JsValue] = Try(Json.parse(text))

  private def parseFields(js: JsValue): Either[RzError, List[Field]] = {}

  private def buildFields(absolutePath: String) = readTxt(Paths.get(absolutePath, "schema.json").toFile) match {
    case Success(l) =>
      parseJson(l.mkString) match {
        case Success(value) =>
          parseFields(value) match {
            case Right(v) => v
            case Left(e) =>
              logger.error(e.toString)
              List()
          }
        case Failure(_) => List[Field]()
      }
    case Failure(_) => List[Field]()
  }

  private def buildTemplate(path: File): Template = {
    val name = path.getName.split('-').map(_.capitalize).mkString(" ")
    val description: List[String] = readTxt(Paths.get(path.getAbsolutePath, "readme.txt").toFile) match {
      case Success(l) => l
      case Failure(_) => List()
    }
    val fields = buildFields(path.getAbsolutePath)
    Template(name, description, fields)
  }

  def list: Array[Template] =
    if (templatesDir.exists && templatesDir.isDirectory) {
      templatesDir.listFiles.filter(_.isDirectory).map(file => buildTemplate(file))
    } else {
      logger.warn("Template folder does not exist")
      Array[Template]()
    }
}
