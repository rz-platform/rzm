package documents.validations

import documents.models.UploadFileForm
import infrastructure.validations.RzConstraints
import play.api.data.Form
import play.api.data.Forms.{ mapping, nonEmptyText }

object UploadForms {
  val uploadForm: Form[UploadFileForm] = Form(
    mapping("path" -> nonEmptyText.verifying(RzConstraints.checkPathForExcludedSymbols))(UploadFileForm.apply)(
      UploadFileForm.unapply
    )
  )
}
