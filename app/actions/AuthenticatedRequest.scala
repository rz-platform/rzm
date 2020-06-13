package actions

import controllers.{AccountController, routes}
import javax.inject.{Inject, Singleton}
import models.{SessionName, UserRequest}
import play.api.i18n.MessagesApi
import play.api.mvc._
import repositories.AccountRepository

import scala.concurrent.{ExecutionContext, Future}

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
      sessionId <- request.session.get(SessionName.toString)
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
