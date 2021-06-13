package forms

import infrastructure.RzDateTime
import play.api.data.validation.{ Constraint, Invalid, Valid, ValidationError }

object ForbiddenSymbols {
  private val pathForbiddenSymbols: List[String]    = List("?", ":", "#", "&", "..", "$", "%")
  private val generalForbiddenSymbols: List[String] = pathForbiddenSymbols :+ "/"

  def isPathValid(itemName: String): Boolean = pathForbiddenSymbols.exists(itemName contains _)

  def isNameValid(itemName: String): Boolean = generalForbiddenSymbols.exists(itemName contains _)

  override def toString: String = generalForbiddenSymbols.mkString("") // for testing purposes
}

object RzConstraints {
  val timeZoneConstraint: Constraint[String] = Constraint({ tz: String =>
    if (RzDateTime.zoneIds.contains(tz)) {
      Valid
    } else {
      Invalid(Seq(ValidationError("")))
    }
  })

  val checkNameForExcludedSymbols: Constraint[String] = Constraint[String] { itemName: String =>
    if (!ForbiddenSymbols.isNameValid(itemName)) Valid
    else Invalid(Seq(ValidationError("repository.edit.invalid.name")))
  }

  val checkPathForExcludedSymbols: Constraint[String] = Constraint[String] { itemName: String =>
    if (!ForbiddenSymbols.isPathValid(itemName)) Valid
    else Invalid(Seq(ValidationError("repository.edit.invalid.name")))
  }

}
