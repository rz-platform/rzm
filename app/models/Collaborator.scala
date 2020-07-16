package models

sealed trait AccessLevel {
  def role: Integer
}

case object OwnerAccess extends AccessLevel {
  val role = 0

  override def toString: String = "owner"
}

case object EditAccess extends AccessLevel {
  val role = 20

  override def toString: String = "edit"
}

case object ViewAccess extends AccessLevel {
  val role = 30

  override def toString: String = "view"
}

object AccessLevel {
  def fromString(accessLevel: String): Option[AccessLevel] =
    accessLevel match {
      case _ if accessLevel == OwnerAccess.toString => Some(OwnerAccess)
      case _ if accessLevel == EditAccess.toString  => Some(EditAccess)
      case _ if accessLevel == ViewAccess.toString  => Some(ViewAccess)
      case _                                        => Option.empty[AccessLevel]
    }

  def fromRole(role: Int): Option[AccessLevel] =
    role match {
      case _ if role == OwnerAccess.role => Some(OwnerAccess)
      case _ if role == EditAccess.role  => Some(EditAccess)
      case _ if role == ViewAccess.role  => Some(ViewAccess)
      case _                             => Option.empty[AccessLevel]
    }

  def userAccess(collaborator: Option[Int], repositoryOwnerId: Long, accountId: Long): Option[AccessLevel] =
    collaborator match {
      case None if repositoryOwnerId == accountId => Some(OwnerAccess)
      case Some(accessLevel)                      => Some(AccessLevel.fromRole(accessLevel).getOrElse(ViewAccess))
      case _                                      => Option.empty[AccessLevel]
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
