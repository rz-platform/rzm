package collaborators.models

import documents.models.RzRepository
import users.models.Account

import scala.util.Try

case class Collaborator(account: Account, repo: RzRepository, role: Role)

object Collaborator {
  def make(account: Account, repo: RzRepository, permS: String): Option[Collaborator] =
    for {
      perm <- Try(permS.toInt).toOption
      role <- Role.fromPermission(perm)
    } yield Collaborator(account, repo, role)
}
