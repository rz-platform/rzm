package documents.validations

import documents.models.{EditedItem, NewItem, RzPathUrl}
import infrastructure.validations.RzConstraints
import play.api.data.Form
import play.api.data.Forms.{boolean, mapping, nonEmptyText, optional, text}

object EditorForms {
  val editorForm: Form[EditedItem] = Form(
    mapping(
      "content" -> text,
      "rev"     -> nonEmptyText,
      "path"    -> nonEmptyText.verifying(RzConstraints.checkPathForExcludedSymbols),
      "name"    -> optional(text.verifying(RzConstraints.checkNameForExcludedSymbols))
    )(EditedItem.apply)(EditedItem.unapply)
  )

  val addNewItemForm: Form[NewItem] = Form(
    mapping(
      "name"     -> nonEmptyText.verifying(RzConstraints.checkNameForExcludedSymbols),
      "rev"      -> nonEmptyText,
      "path"     -> nonEmptyText.verifying(RzConstraints.checkPathForExcludedSymbols),
      "isFolder" -> boolean
    )(NewItem.apply)(NewItem.unapply)
  )

  def clean(data: Option[Map[String, Seq[String]]]): Map[String, Seq[String]] = {
    val form: Map[String, Seq[String]] = data.getOrElse(collection.immutable.Map[String, Seq[String]]())
    form.map {
      case (key, values) if key == "name" => (key, values.map(RzPathUrl.make(_).uri.trim))
      case (key, values) if key == "path" => (key, values.map(RzPathUrl.make(_).uri.trim))
      case (key, values)                  => (key, values)
    }
  }
}
