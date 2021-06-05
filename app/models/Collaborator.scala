package models

import infrastructure.RzDateTime

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

      case _ => Option.empty[Role]
    }
}

case class Collaborator(
  id: String,
  account: Account,
  role: Role,
  createdAt: Long
) extends PersistentEntityMap {
  val prefix = RedisKeyPrefix.collaboratorPrefix

  def toMap: Map[String, String] =
    Map("account" -> account.id, "role" -> role.name, "createdAt" -> createdAt.toString)

  def this(account: Account, role: Role) =
    this(PersistentEntity.id, account, role, RzDateTime.now)
}

object Collaborator {
  def key(id: String): String = PersistentEntity.key(RedisKeyPrefix.collaboratorPrefix, id)

  def make(id: String, account: Account, data: Map[String, String]): Either[RzError, Collaborator] =
    (for {
      role      <- data.get("role")
      role      <- Role.fromString(role)
      createdAt <- data.get("createdAt")
    } yield Collaborator(id, account, role, createdAt.toInt)) match {
      case Some(a) => Right(a)
      case None    => Left(ParsingError)
    }
}
