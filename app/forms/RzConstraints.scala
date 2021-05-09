package forms

import play.api.data.validation.{ Constraint, Invalid, Valid, ValidationError }
import repositories.TimezoneOffsetRepository

object RzConstraints {
  val timeZoneConstraint: Constraint[String] = Constraint({ tz: String =>
    if (TimezoneOffsetRepository.zoneIds.contains(tz)) {
      Valid
    } else {
      Invalid(Seq(ValidationError("")))
    }
  })

}
