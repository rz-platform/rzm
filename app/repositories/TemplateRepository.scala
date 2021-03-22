package repositories

import models._
import play.api.Configuration
import play.api.libs.json._

import java.io.File
import java.nio.file.Paths
import javax.inject.{ Inject, Singleton }
import scala.collection.SortedMap
import scala.io.Source
import scala.util.{ Failure, Success, Try, Using }

@Singleton
class TemplateRepository @Inject() (config: Configuration) {
  private val logger = play.api.Logger(this.getClass)

  // templates directory root
  val dir = new File(config.get[String]("play.server.templates.dir"))

  def get(name: String): Option[Template] = list.get(name)

  def list: SortedMap[String, Template] =
    if (dir.exists && dir.isDirectory) {
      val l: Array[Template] =
        dir.listFiles.filter(filterTemplates).map(file => buildTemplate(file)).sortBy(t => t.name)
      val t: Array[(String, Template)] = l.map(t => Tuple2(t.name, t))
      SortedMap.from(l.map(t => Tuple2(t.name, t)))
    } else {
      logger.warn("Template folder does not exist")
      SortedMap[String, Template]()
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

    Template(name, description, path, entypoint, exampleFile, fields)
  }

  private def readTxt(filename: File): Try[List[String]] =
    Using(Source.fromFile(filename))(source => (for (line <- source.getLines) yield line).toList)

  private def parseFields(js: JsValue): List[Field] =
    (js \ "fields").getOrElse(JsArray()).as[JsArray].value.map(obj => parseField(obj)).toList.collect {
      case Success(f) => f
    }

  private def parseField(obj: JsValue): Try[Field] =
    (obj \ "type").getOrElse(JsString("")).as[String] match {
      case Numeric.t  => Try(obj.as[Numeric])
      case Choice.t   => Try(obj.as[Choice])
      case Checkbox.t => Try(obj.as[Checkbox])
      case _          => Failure(JsonParseError)
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

  private def filterTemplates(f: File): Boolean =
    f.isDirectory && f.getName.take(1) != "." && f.getName.takeRight(1) != "~"
}
