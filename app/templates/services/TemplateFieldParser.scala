package templates.services

import play.api.libs.json.{ JsArray, JsString, JsValue }
import templates.errors.JsonParseError
import templates.models
import templates.models.{ Checkbox, Choice, RzField }

import scala.util.{ Failure, Success, Try }

object TemplateFieldParser {
  def parseFields(js: JsValue): List[RzField] =
    (js \ "fields")
      .getOrElse(JsArray())
      .as[JsArray]
      .value
      .map(obj => parseField(obj))
      .toList
      .collect {
        case Success(f) => f
      }

  def parseArg(js: JsValue, argName: String): Option[String] =
    (js \ argName).toOption.flatMap { js: JsValue =>
      Try(js.as[String]) match {
        case Success(v) => Some(v)
        case Failure(_) => None
      }
    }

  private def parseField(obj: JsValue): Try[RzField] =
    (obj \ "type").getOrElse(JsString("")).as[String] match {
      case models.Numeric.t => Try(obj.as[models.Numeric])
      case Choice.t         => Try(obj.as[Choice])
      case Checkbox.t       => Try(obj.as[Checkbox])
      case _                => Failure(JsonParseError)
    }
}
