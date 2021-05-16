package forms

import models.AccountLoginData
import play.api.data.Form
import play.api.data.Forms.{ mapping, nonEmptyText }

object AuthForms {
  val signin: Form[AccountLoginData] = Form(
    mapping(
      "userName" -> nonEmptyText,
      "password" -> nonEmptyText,
      "timezone" -> nonEmptyText.verifying(RzConstraints.timeZoneConstraint)
    )(AccountLoginData.apply)(AccountLoginData.unapply)
  )
}
