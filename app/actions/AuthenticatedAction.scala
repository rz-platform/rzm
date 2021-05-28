package actions

import controllers.routes
import services.encryption.{ EncryptionService, UserInfoCookieBakerFactory }
import models.{ Account, AccountRequest, Auth, UserInfo }
import play.api.i18n.MessagesApi
import play.api.mvc._
import repositories.{ AccountRepository, SessionRepository }

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

/**
 * An action that pulls everything together to show user info that is in an encrypted cookie,
 * with only the secret key stored on the server.
 */
class AuthenticatedAction @Inject() (
  accountService: AccountRepository,
  factory: UserInfoCookieBakerFactory,
  encryptionService: EncryptionService,
  sessionRepository: SessionRepository,
  playBodyParsers: PlayBodyParsers,
  messagesApi: MessagesApi
)(implicit val executionContext: ExecutionContext)
    extends ActionBuilder[AccountRequest, AnyContent]
    with Results {

  override def parser: BodyParser[AnyContent] = playBodyParsers.anyContent

  def createSession(userInfo: UserInfo): Future[(String, Cookie)] = {
    // create a user info cookie with this specific secret key
    val secretKey      = encryptionService.newSecretKey
    val cookieBaker    = factory.createCookieBaker(secretKey)
    val userInfoCookie = cookieBaker.encodeAsCookie(Some(userInfo))

    // Tie the secret key to a session id, and store the encrypted data in client side cookie
    sessionRepository.create(secretKey).map(sessionId => (sessionId, userInfoCookie))
  }

  def authorize(account: Account, s: Session): Future[Result] = {
    val userInfo = UserInfo(account.userName)
    createSession(userInfo).map {
      case (sessionId, encryptedCookie) =>
        val session: Session = s + (Auth.sessionId -> sessionId)
        Redirect(routes.RzRepositoryController.list())
          .withSession(session)
          .withCookies(encryptedCookie)
    }
  }

  def discardingSession(result: Result): Result =
    result.withNewSession.discardingCookies(DiscardingCookie(Auth.userInfoCookie))

  def redirect: Future[Result] = Future.successful {
    discardingSession {
      Redirect(routes.AuthController.loginPage())
    }
  }

  private def getAccountByCookie(user: Option[UserInfo]): Future[Option[Account]] = user match {
    case Some(info) =>
      accountService.getByUsernameOrEmail(info.username).map {
        case Right(account: Account) => Some(account)
        case _                       => None
      }
    case _ => Future(None)
  }

  override def invokeBlock[A](
    request: Request[A],
    block: AccountRequest[A] => Future[Result]
  ): Future[Result] = {
    val maybeFutureResult: Option[Future[Result]] = for {
      sessionId      <- request.session.get(Auth.sessionId)
      userInfoCookie <- request.cookies.get(Auth.userInfoCookie)
    } yield {
      sessionRepository.lookup(sessionId).flatMap {
        case Some(secretKey) =>
          val cookieBaker = factory.createCookieBaker(secretKey)

          getAccountByCookie(cookieBaker.decodeFromCookie(Some(userInfoCookie))).flatMap {
            case Some(account: Any) => block(new AccountRequest[A](request, account, messagesApi))
            case _                  => redirect
          }
        case None =>
          // We've got a user with a client session id, but no server-side state.
          // Let's redirect them back to the home page without any session cookie stuff.
          redirect
      }
    }

    maybeFutureResult.getOrElse(redirect)
  }
}
