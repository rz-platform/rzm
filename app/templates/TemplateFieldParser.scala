package templates

import models.{ Checkbox, Choice, Field, Numeric }
import play.api.libs.json.{ JsArray, JsString, JsValue }
import repositories.JsonParseError

import scala.util.{ Failure, Success, Try }

object TemplateFieldParser {
  def parseFields(js: JsValue): List[Field] =
    (js \ "fields")
      .getOrElse(JsArray())
      .as[JsArray]
      .value
      .map(obj => parseField(obj))
      .toList
      .collect {
        case Success(f) => f
      }

  def parseEntrypoint(js: JsValue): Option[String] =
    (js \ "entrypoint").toOption.flatMap { js: JsValue =>
      Try(js.as[String]) match {
        case Success(v) => Some(v)
        case Failure(_) => None
      }
    }

  private def parseField(obj: JsValue): Try[Field] =
    (obj \ "type").getOrElse(JsString("")).as[String] match {
      case Numeric.t  => Try(obj.as[Numeric])
      case Choice.t   => Try(obj.as[Choice])
      case Checkbox.t => Try(obj.as[Checkbox])
      case _          => Failure(JsonParseError)
    }
}
