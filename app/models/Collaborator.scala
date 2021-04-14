package models

import repositories.{ ParsingError, RzError }

sealed trait Role {
  def weight: Int
  def name: String
}

object Role {
  case object Owner extends Role {
    val weight = 100

    val name: String = "owner"
  }

  case object Editor extends Role {
    val weight = 90

    val name: String = "editor"
  }

  case object Viewer extends Role {
    val weight = 80

    val name: String = "viewer"
  }

  def fromString(role: String): Option[Role] =
    role match {
      case _ if role == Owner.name  => Some(Owner)
      case _ if role == Editor.name => Some(Editor)
      case _ if role == Viewer.name => Some(Viewer)
      case _                        => Option.empty[Role]
    }
}

case class Collaborator(
  account: Account,
  role: Role,
  createdAt: Long
) {
  def keyAccessLevel(repo: RzRepository): String =
    IdTable.collaboratorPrefix + repo.owner.userName + ":" + repo.name + ":" + account.userName

  def toMap: Map[String, String] = Map("role" -> role.name, "createdAt" -> createdAt.toString)

  def this(account: Account, accessLevel: Role) = this(account, accessLevel, DateTime.now)
}

object Collaborator {
  def keyAccessLevel(account: Account, repo: RzRepository): String =
    IdTable.collaboratorPrefix + repo.owner.userName + ":" + repo.name + ":" + account.userName

  def make(account: Account, data: Map[String, String]): Either[RzError, Collaborator] =
    (for {
      role      <- data.get("role")
      role      <- Role.fromString(role)
      createdAt <- data.get("createdAt")
    } yield Collaborator(account, role, createdAt.toInt)) match {
      case Some(a) => Right(a)
      case None    => Left(ParsingError)
    }
}
