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

  def list: Array[Template] =
    if (templatesDir.exists && templatesDir.isDirectory) {
      templatesDir.listFiles.filter(filterTemplates).map(file => buildTemplate(file)).sortBy(t => t.name)
    } else {
      logger.warn("Template folder does not exist")
      Array[Template]()
    }

  private def readTxt(filename: File): Try[List[String]] =
    Using(Source.fromFile(filename))(source => (for (line <- source.getLines) yield line).toList)

  private def parseFields(js: JsValue): List[Field] =
    (js \ "fields").getOrElse(JsArray()).as[JsArray].value.map(obj => parseField(obj)).toList.flatten

  private def parseField(obj: JsValue): Option[Field] =
    (obj \ "type").getOrElse(JsString("")).as[String] match {
      case Numeric.t  => Some(obj.as[Numeric])
      case Choice.t   => Some(obj.as[Choice])
      case Checkbox.t => Some(obj.as[Checkbox])
      case _          => None
    }

  private def parseEntrypoint(js: JsValue): Option[String] =
    (js \ "entrypoint").toOption.flatMap { js: JsValue =>
      Try(js.as[String]) match {
        case Success(v) => Some(v)
        case Failure(_) => None
      }
    }

  private def readJson(absolutePath: String): Either[RzError, JsValue] =
    readTxt(Paths.get(absolutePath, "schema.json").toFile) match {
      case Success(l) =>
        Try(Json.parse(l.mkString)) match {
          case Success(v) => Right(v)
          case Failure(_) => Left(JsonParseError)
        }
      case Failure(_) => Left(FileNotFound)
    }

  private def getExampleFile(entrypoint: Option[String]): Option[String] = entrypoint match {
    case Some(e) => Some(s"${e.replaceFirst("[.][^.]+$", "")}.pdf")
    case _       => None
  }

  private def buildTemplate(path: File): Template = {
    val name         = path.getName.split('-').map(_.capitalize).mkString(" ")
    val absolutePath = path.getAbsolutePath
    val description: List[String] = readTxt(Paths.get(absolutePath, "readme.txt").toFile) match {
      case Success(l) => l
      case Failure(_) => List()
    }
    val js = readJson(absolutePath)
    val fields = js match {
      case Right(v) => parseFields(v)
      case Left(_)  => List()
    }
    val entypoint = js match {
      case Right(v) => parseEntrypoint(v)
      case Left(_)  => None
    }
    val exampleFile = getExampleFile(entypoint)
    Template(name, description, entypoint, exampleFile, fields)
  }

  private def filterTemplates(f: File): Boolean =
    f.isDirectory && f.getName.take(1) != "." && f.getName.takeRight(1) != "~"
}
