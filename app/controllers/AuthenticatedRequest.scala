package controllers

import javax.inject.{ Inject, Singleton }
import models.{ Repository, SimpleAccount }
import play.api.i18n.MessagesApi
import play.api.mvc._
import repositories.AccountRepository

import scala.concurrent.{ ExecutionContext, Future }

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

/**
 * An action that pulls everything together to show user info that is in an encrypted cookie,
 * with only the secret key stored on the server.
 */
@Singleton
class AuthenticatedRequest @Inject() (
  accountService: AccountRepository,
  playBodyParsers: PlayBodyParsers,
  messagesApi: MessagesApi
)(implicit val executionContext: ExecutionContext)
    extends ActionBuilder[UserRequest, AnyContent]
    with Results {

  override def parser: BodyParser[AnyContent] = playBodyParsers.anyContent

  override def invokeBlock[A](
    request: Request[A],
    block: UserRequest[A] => Future[Result]
  ): Future[Result] = {
    // deal with the options first, then move to the futures
    val maybeFutureResult: Option[Future[Result]] = for {
      sessionId <- request.session.get(AccountController.SESSION_NAME)
    } yield {
      accountService.findById(sessionId.toLong).flatMap {
        case Some(account) =>
          block(new UserRequest[A](request, account, messagesApi))
        case None =>
          Future.successful {
            Redirect(routes.AccountController.login()).withNewSession.flashing("error" -> "unauthorized")
          }
      }
    }

    maybeFutureResult.getOrElse {
      Future.successful {
        Redirect(routes.AccountController.login()).withNewSession.flashing("error" -> "unauthorized")
      }
    }
  }
}
