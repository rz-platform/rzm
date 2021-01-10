package models

import repositories.{ ParsingError, RzError }

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

  def fromAccount(collaborator: Option[Int], repositoryOwnerId: Long, accountId: Long): Option[AccessLevel] =
    collaborator match {
      case None if repositoryOwnerId == accountId => Some(OwnerAccess)
      case Some(accessLevel)                      => Some(AccessLevel.fromRole(accessLevel).getOrElse(ViewAccess))
      case _                                      => Option.empty[AccessLevel]
    }

}

case class Collaborator(
  account: Account,
  role: AccessLevel,
  createdAt: Long
) {
  def keyAccessLevel(repo: RzRepository): String =
    IdTable.collaboratorPrefix + repo.owner.userName + ":" + repo.name + ":" + account.userName

  def toMap: Map[String, String] = Map("role" -> role.role.toString, "createdAt" -> createdAt.toString)

  def this(account: Account, accessLevel: AccessLevel) = this(account, accessLevel, DateTime.now)
}

object Collaborator {
  def keyAccessLevel(account: Account, repo: RzRepository): String =
    IdTable.collaboratorPrefix + repo.owner.userName + ":" + repo.name + ":" + account.userName

  def make(account: Account, data: Map[String, String]): Either[RzError, Collaborator] = {
    val a = for {
      role        <- data.get("role")
      accessLevel <- AccessLevel.fromRole(role.toInt)
      createdAt   <- data.get("createdAt")
    } yield Collaborator(account, accessLevel, createdAt.toInt)
    a match {
      case Some(a) => Right(a)
      case None    => Left(ParsingError)
    }
  }
}
