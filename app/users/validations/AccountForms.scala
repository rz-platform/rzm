package users.validations

import infrastructure.validations.RzConstraints
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints.pattern
import users.models._

import scala.util.matching.Regex

object AccountForms {
  val onlyAlphabet: Regex = "^[A-Za-z\\d_\\-]+$".r

  val signupForm: Form[UserRegistration] = Form(
    mapping(
      "username"    -> text(maxLength = 36).verifying(pattern(onlyAlphabet)),
      "fullName"    -> optional(text(maxLength = 36)),
      "password"    -> nonEmptyText(maxLength = 255),
      "timezone"    -> nonEmptyText.verifying(RzConstraints.timeZoneConstraint),
      "mailAddress" -> email
    )(UserRegistration.apply)(UserRegistration.unapply)
  )

  val accountEditForm: Form[UserUpdate] = Form(
    mapping(
      "username"    -> nonEmptyText,
      "fullName"    -> optional(text(maxLength = 25)),
      "mailAddress" -> email
    )(UserUpdate.apply)(UserUpdate.unapply)
  )

  val updatePasswordForm: Form[PasswordUpdate] = Form(
    mapping(
      "oldPassword" -> nonEmptyText,
      "newPassword" -> nonEmptyText
    )(PasswordUpdate.apply)(PasswordUpdate.unapply)
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
      case (key, values) if key == "username"    => (key, values.map(_.trim.toLowerCase()))
      case (key, values) if key == "fullName"    => (key, values.map(_.trim.capitalize))
      case (key, values)                         => (key, values)
    }
  }

  def filledAccountEditForm(account: Account): Form[UserUpdate] =
    accountEditForm.fill(
      UserUpdate(account.username, Some(account.fullname), account.email)
    )

}
