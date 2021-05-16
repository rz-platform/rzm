package forms

import models.UploadFileForm
import play.api.data.Form
import play.api.data.Forms.{ mapping, nonEmptyText }

object UploadForms {
  val uploadForm: Form[UploadFileForm] = Form(
    mapping("path" -> nonEmptyText.verifying(RzConstraints.checkPathForExcludedSymbols))(UploadFileForm.apply)(
      UploadFileForm.unapply
    )
  )
}
