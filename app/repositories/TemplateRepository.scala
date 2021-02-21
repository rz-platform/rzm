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

  private def parseJson(text: String): Try[JsArray] = Try(Json.parse(text).as[JsArray])

  private def parseField(obj: JsValue): Option[Field] =
    (obj \ "type").getOrElse(JsString("")).as[String] match {
      case Numeric.t  => Some(obj.as[Numeric])
      case Choice.t   => Some(obj.as[Choice])
      case Checkbox.t => Some(obj.as[Checkbox])
      case _          => None
    }

  private def parseFields(js: JsArray): List[Field] = js.value.map(obj => parseField(obj)).toList.flatten

  private def buildFields(absolutePath: String): List[Field] =
    readTxt(Paths.get(absolutePath, "schema.json").toFile) match {
      case Success(l) =>
        parseJson(l.mkString) match {
          case Success(value) => parseFields(value)
          case Failure(_)     => List[Field]()
        }
      case Failure(_) => List[Field]()
    }

  private def buildTemplate(path: File): Template = {
    val name         = path.getName.split('-').map(_.capitalize).mkString(" ")
    val absolutePath = path.getAbsolutePath
    val description: List[String] = readTxt(Paths.get(absolutePath, "readme.txt").toFile) match {
      case Success(l) => l
      case Failure(_) => List()
    }
    val fields = buildFields(absolutePath)
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
