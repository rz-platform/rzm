package forms

import models.{ NewCollaboratorData, RemoveCollaboratorData }
import play.api.data.Form
import play.api.data.Forms.{ mapping, nonEmptyText }

object CollaboratorForms {
  val addCollaboratorForm: Form[NewCollaboratorData] = Form(
    mapping("emailOrLogin" -> nonEmptyText, "role" -> nonEmptyText)(NewCollaboratorData.apply)(
      NewCollaboratorData.unapply
    )
  )
  val removeCollaboratorForm: Form[RemoveCollaboratorData] = Form(
    mapping("email" -> nonEmptyText)(RemoveCollaboratorData.apply)(RemoveCollaboratorData.unapply)
  )

}
