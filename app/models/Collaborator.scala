package models

import scala.util.Try

sealed trait Role {
  def perm: Int
}

object Role {
  val exec  = 1
  val write = 2
  val read  = 4

  case object Owner extends Role {
    val perm: Int = exec | write | read
  }

  case object Editor extends Role {
    val perm: Int = write | read
  }

  case object Viewer extends Role {
    val perm: Int = exec
  }

  def fromPermission(perm: Int): Option[Role] =
    perm match {
      case _ if perm == Owner.perm  => Some(Owner)
      case _ if perm == Editor.perm => Some(Editor)
      case _ if perm == Viewer.perm => Some(Viewer)

      case _ => Option.empty[Role]
    }
}

case class Collaborator(account: Account, repo: RzRepository, role: Role)

object Collaborator {
  def make(account: Account, repo: RzRepository, permS: String): Option[Collaborator] =
    for {
      perm <- Try(permS.toInt).toOption
      role <- Role.fromPermission(perm)
    } yield Collaborator(account, repo, role)
}
