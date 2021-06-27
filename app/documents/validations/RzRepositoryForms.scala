package documents.validations

import documents.models.RepositoryDetails
import play.api.data.Form
import play.api.data.Forms.{ mapping, nonEmptyText, optional, text }
import play.api.data.validation.Constraints.pattern
import users.validations.AccountForms

object RzRepositoryForms {
  val createRepositoryForm: Form[RepositoryDetails] = Form(
    mapping(
      "name"        -> nonEmptyText(minLength = 1, maxLength = 36).verifying(pattern(AccountForms.onlyAlphabet)),
      "description" -> optional(text(maxLength = 255))
    )(RepositoryDetails.apply)(RepositoryDetails.unapply)
  )

  def clear(data: Option[Map[String, Seq[String]]]): Map[String, Seq[String]] = {
    val form: Map[String, Seq[String]] = data.getOrElse(collection.immutable.Map[String, Seq[String]]())
    form.map {
      case (key, values) if key == "name"        => (key, values.map(_.trim.toLowerCase()))
      case (key, values) if key == "description" => (key, values.map(_.trim))
      case (key, values)                         => (key, values)
    }
  }
}
