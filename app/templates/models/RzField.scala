package templates.models

import play.api.libs.functional.syntax._
import play.api.libs.json.{ JsPath, Reads }

sealed trait RzField {
  def name: String
  def label: String
  def description: Option[String]
}

case class Numeric(name: String, label: String, description: Option[String], min: Int, max: Int, default: Int)
    extends RzField
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
    extends RzField
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

case class Checkbox(name: String, label: String, description: Option[String], default: Boolean) extends RzField
object Checkbox {
  val t = "checkbox"

  implicit val reads: Reads[Checkbox] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "label").read[String] and
      (JsPath \ "description").readNullable[String] and
      (JsPath \ "default").read[Boolean]
  )(Checkbox.apply _)
}
