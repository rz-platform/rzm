package forms

import models.{ RzRegex, SshKeyData, SshRemoveData }
import play.api.data.Form
import play.api.data.Forms.{ mapping, nonEmptyText }
import play.api.data.validation.Constraints.pattern

object SshForms {
  val addSshKeyForm: Form[SshKeyData] = Form(
    mapping(
      "publicKey" -> nonEmptyText.verifying(
        pattern(RzRegex.publicKey)
      )
    )(SshKeyData.apply)(SshKeyData.unapply)
  )
  val deleteSshKeyForm: Form[SshRemoveData] = Form(
    mapping(
      "id" -> nonEmptyText
    )(SshRemoveData.apply)(SshRemoveData.unapply)
  )

}
