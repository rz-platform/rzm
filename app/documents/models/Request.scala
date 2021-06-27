package documents.models

import collaborators.models.Role
import play.api.i18n.MessagesApi
import play.api.mvc._
import users.models.{ Account, AccountRequest, RequestWithAccount }

trait RepositoryRequestHeader extends PreferredMessagesProvider with MessagesRequestHeader with RequestWithAccount {
  def account: Account
  def repository: RzRepository
  def role: Role
}

class RepositoryRequest[A](
  request: AccountRequest[A],
  val repository: RzRepository,
  val account: Account,
  val role: Role,
  val messagesApi: MessagesApi
) extends WrappedRequest[A](request)
    with RepositoryRequestHeader
