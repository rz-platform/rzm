package models
import play.api.i18n.MessagesApi
import play.api.mvc._

trait RequestWithAccount extends PreferredMessagesProvider with MessagesRequestHeader {
  def account: SimpleAccount
}

trait AccountRequestHeader extends PreferredMessagesProvider with MessagesRequestHeader with RequestWithAccount {
  def account: SimpleAccount
}
class AccountRequest[A](request: Request[A], val account: SimpleAccount, val messagesApi: MessagesApi)
    extends WrappedRequest[A](request)
    with AccountRequestHeader

trait RepositoryRequestHeader extends PreferredMessagesProvider with MessagesRequestHeader with RequestWithAccount {
  def account: SimpleAccount
  def repository: Repository
  def role: AccessLevel
}

class RepositoryRequest[A](
  request: AccountRequest[A],
  val repository: Repository,
  val account: SimpleAccount,
  val role: AccessLevel,
  val messagesApi: MessagesApi
) extends WrappedRequest[A](request)
    with RepositoryRequestHeader
