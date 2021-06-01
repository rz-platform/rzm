package forms

import infrastructure.RzDateTime
import models.ForbiddenSymbols
import play.api.data.validation.{ Constraint, Invalid, Valid, ValidationError }

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
