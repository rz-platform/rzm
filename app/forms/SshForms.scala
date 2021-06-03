package forms

import models.{RzRegex, SshKeyData, SshRemoveData}
import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText}
import play.api.data.validation.Constraints.pattern

import scala.util.matching.Regex

object SshForms {
  val publicKeyRegex: Regex =
    "^(ssh-rsa AAAAB3NzaC1yc2|ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNT|ssh-ed25519 AAAAC3NzaC1lZDI1NTE5|ssh-dss AAAAB3NzaC1kc3)[0-9A-Za-z+/]+[=]{0,3}( [^@]+@[^@]+)?$".r


  val addSshKeyForm: Form[SshKeyData] = Form(
    mapping(
      "publicKey" -> nonEmptyText.verifying(
        pattern(publicKeyRegex)
      )
    )(SshKeyData.apply)(SshKeyData.unapply)
  )
  val deleteSshKeyForm: Form[SshRemoveData] = Form(
    mapping(
      "id" -> nonEmptyText
    )(SshRemoveData.apply)(SshRemoveData.unapply)
  )

}
