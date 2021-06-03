package forms

import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints.pattern

object AccountForms {
  val signupForm: Form[AccountRegistrationData] = Form(
    mapping(
      "userName"    -> text(maxLength = 36).verifying(pattern(RzRegex.onlyAlphabet)),
      "fullName"    -> optional(text(maxLength = 36)),
      "password"    -> nonEmptyText(maxLength = 255),
      "timezone"    -> nonEmptyText.verifying(RzConstraints.timeZoneConstraint),
      "mailAddress" -> email
    )(AccountRegistrationData.apply)(AccountRegistrationData.unapply)
  )

  val accountEditForm: Form[AccountData] = Form(
    mapping(
      "userName"    -> nonEmptyText,
      "fullName"    -> optional(text(maxLength = 25)),
      "mailAddress" -> email
    )(AccountData.apply)(AccountData.unapply)
  )

  val updatePasswordForm: Form[PasswordData] = Form(
    mapping(
      "oldPassword" -> nonEmptyText,
      "newPassword" -> nonEmptyText
    )(PasswordData.apply)(PasswordData.unapply)
  )

  val timeZoneForm: Form[TimeZoneData] = Form(
    mapping(
      "timezone" -> nonEmptyText.verifying(RzConstraints.timeZoneConstraint)
    )(TimeZoneData.apply)(TimeZoneData.unapply)
  )

  def clear(data: Option[Map[String, Seq[String]]]): Map[String, Seq[String]] = {
    val form: Map[String, Seq[String]] = data.getOrElse(collection.immutable.Map[String, Seq[String]]())
    form.map {
      case (key, values) if key == "mailAddress" => (key, values.map(_.trim.toLowerCase()))
      case (key, values) if key == "userName"    => (key, values.map(_.trim.toLowerCase()))
      case (key, values) if key == "fullName"    => (key, values.map(_.trim.capitalize))
      case (key, values)                         => (key, values)
    }
  }

  def filledAccountEditForm(account: Account): Form[AccountData] =
    accountEditForm.fill(
      AccountData(account.username, Some(account.fullname), account.email)
    )

}
