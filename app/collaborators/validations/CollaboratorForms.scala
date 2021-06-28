package collaborators.validations

import collaborators.models.{ NewCollaboratorDetails, RemoveCollaboratorDetails }
import play.api.data.Form
import play.api.data.Forms.{ mapping, nonEmptyText }

object CollaboratorForms {
  val addCollaboratorForm: Form[NewCollaboratorDetails] = Form(
    mapping("emailOrLogin" -> nonEmptyText, "role" -> nonEmptyText)(NewCollaboratorDetails.apply)(
      NewCollaboratorDetails.unapply
    )
  )
  val removeCollaboratorForm: Form[RemoveCollaboratorDetails] = Form(
    mapping("email" -> nonEmptyText)(RemoveCollaboratorDetails.apply)(RemoveCollaboratorDetails.unapply)
  )

}
