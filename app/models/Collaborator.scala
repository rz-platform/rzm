package models

sealed trait AccessLevel {
  def role: Integer
}

case object Owner extends AccessLevel {
  val role = 0

  override def toString: String = "owner"
}

case object Edit extends AccessLevel {
  val role = 20

  override def toString: String = "edit"
}

case object View extends AccessLevel {
  val role = 30

  override def toString: String = "view"
}

object AccessLevel {
  def fromString(accessLevel: String): Option[AccessLevel] =
    accessLevel match {
      case _ if accessLevel == Owner.productPrefix.toLowerCase => Some(Owner)
      case _ if accessLevel == Edit.productPrefix.toLowerCase  => Some(Edit)
      case _                                                   => Option.empty[AccessLevel]
    }

  def fromRole(role: Int): Option[AccessLevel] =
    role match {
      case _ if role == Owner.role => Some(Owner)
      case _ if role == Edit.role  => Some(Edit)
      case _                       => Option.empty[AccessLevel]
    }

}

case class Collaborator(
  account: Account,
  role: Int
) {
  val accessLevel: Option[AccessLevel] = AccessLevel.fromRole(role)
}

case class NewCollaboratorData(emailOrLogin: String, accessLevel: String)

case class RemoveCollaboratorData(email: String)
