package users.validations

import authentication.models.EmailAndPasswordCredentials
import infrastructure.validations.RzConstraints
import play.api.data.Form
import play.api.data.Forms.{ mapping, nonEmptyText }

object AuthForms {
  val signin: Form[EmailAndPasswordCredentials] = Form(
    mapping(
      "username" -> nonEmptyText,
      "password" -> nonEmptyText,
      "timezone" -> nonEmptyText.verifying(RzConstraints.timeZoneConstraint)
    )(EmailAndPasswordCredentials.apply)(EmailAndPasswordCredentials.unapply)
  )
}
