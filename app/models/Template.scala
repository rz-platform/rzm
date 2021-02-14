package models

sealed trait Field {
  def name: String
}

case class Numeric(name: String, min: Int, max: Int) extends Field

case class Choice(name: String, choices: List[String]) extends Field

case class Checkbox(name: String) extends Field

case class Template(name: String, description: List[String], fields: List[Field])
