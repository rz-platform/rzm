package models
import play.api.i18n.MessagesApi
import play.api.mvc._

trait RequestWithAccount extends PreferredMessagesProvider with MessagesRequestHeader {
  def account: Account
}

trait AccountRequestHeader extends PreferredMessagesProvider with MessagesRequestHeader with RequestWithAccount {
  def account: Account
}
class AccountRequest[A](request: Request[A], val account: Account, val messagesApi: MessagesApi)
    extends WrappedRequest[A](request)
    with AccountRequestHeader

trait RepositoryRequestHeader extends PreferredMessagesProvider with MessagesRequestHeader with RequestWithAccount {
  def account: Account
  def repository: RzRepository
  def role: AccessLevel
}

class RepositoryRequest[A](
  request: AccountRequest[A],
  val repository: RzRepository,
  val account: Account,
  val role: AccessLevel,
  val messagesApi: MessagesApi
) extends WrappedRequest[A](request)
    with RepositoryRequestHeader
