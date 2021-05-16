package forms

import models.{ EditedItem, NewItem, RzPathUrl }
import play.api.data.Form
import play.api.data.Forms.{ boolean, mapping, nonEmptyText, text }

object EditorForms {
  val editorForm: Form[EditedItem] = Form(
    mapping(
      "content" -> text,
      "rev"     -> nonEmptyText,
      "path"    -> nonEmptyText.verifying(RzConstraints.checkPathForExcludedSymbols),
      "name"    -> nonEmptyText.verifying(RzConstraints.checkNameForExcludedSymbols)
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
