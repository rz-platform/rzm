package models
import play.api.i18n.MessagesApi
import play.api.mvc._

trait RequestWithUser extends PreferredMessagesProvider with MessagesRequestHeader {
  def account: SimpleAccount
}

trait UserRequestHeader extends PreferredMessagesProvider with MessagesRequestHeader with RequestWithUser {
  def account: SimpleAccount
}
class UserRequest[A](request: Request[A], val account: SimpleAccount, val messagesApi: MessagesApi)
    extends WrappedRequest[A](request)
    with UserRequestHeader

trait RepositoryRequestHeader extends PreferredMessagesProvider with MessagesRequestHeader with RequestWithUser {
  def account: SimpleAccount
  def repository: Repository
  def role: Int
}

class RepositoryRequest[A](
  request: UserRequest[A],
  val repository: Repository,
  val account: SimpleAccount,
  val role: Int,
  val messagesApi: MessagesApi
) extends WrappedRequest[A](request)
    with RepositoryRequestHeader
