package forms

import models.ForbiddenSymbols
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import services.TimeService

object RzConstraints {
  val timeZoneConstraint: Constraint[String] = Constraint({ tz: String =>
    if (TimeService.zoneIds.contains(tz)) {
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
