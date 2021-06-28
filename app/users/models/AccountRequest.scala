package users.models

import play.api.i18n.MessagesApi
import play.api.mvc.{ MessagesRequestHeader, PreferredMessagesProvider, Request, WrappedRequest }

trait RequestWithAccount extends PreferredMessagesProvider with MessagesRequestHeader {
  def account: Account
}

trait AccountRequestHeader extends PreferredMessagesProvider with MessagesRequestHeader with RequestWithAccount {
  def account: Account
}
class AccountRequest[A](request: Request[A], val account: Account, val messagesApi: MessagesApi)
    extends WrappedRequest[A](request)
    with AccountRequestHeader
