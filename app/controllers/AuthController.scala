package controllers

import actions.AuthenticatedAction
import forms.{ AuthForms, FormErrors }
import models._
import play.api.data._
import play.api.i18n.Messages
import play.api.mvc._
import repositories.{ AccountRepository, SessionRepository }
import views.html

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class AuthController @Inject() (
  accountRepository: AccountRepository,
  sessionRepository: SessionRepository,
  authAction: AuthenticatedAction,
  cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  def loginPage: Action[AnyContent] = Action.async(implicit request => Future(Ok(html.signin(AuthForms.signin))))

  def index: Action[AnyContent] =
    authAction.async(implicit req => Future(Redirect(routes.RzRepositoryController.list())))

  def login: Action[AnyContent] = Action.async { implicit req =>
    val successFunc: AccountLoginData => Future[Result] = { accountData: AccountLoginData =>
      checkPassword(accountData.username, accountData.password).flatMap {
        case Right(account) =>
          for {
            (sessionId, encryptedCookie) <- authAction.createSession(AccountInfo(account.id))
            _                            <- accountRepository.setTimezone(account, accountData.timezone)
          } yield {
            val session = req.session + (AuthController.sessionId -> sessionId)
            Redirect(routes.RzRepositoryController.list())
              .withSession(session)
              .withCookies(encryptedCookie)
          }
        case _ =>
          val newForm = FormErrors.error[AccountLoginData](
            AuthForms.signin.bindFromRequest(),
            FormError("username", Messages("signin.error.wrongcred"))
          )
          Future(BadRequest(html.signin(newForm)))
      }
    }

    val errorFunc: Form[AccountLoginData] => Future[Result] = { badForm: Form[AccountLoginData] =>
      Future.successful {
        BadRequest(html.signin(badForm))
      }
    }

    AuthForms.signin.bindFromRequest().fold(errorFunc, successFunc)
  }

  def logout: Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    // When we delete the session id, removing the session id is enough to render the
    // user info cookie unusable.
    req.session.get(AuthController.sessionId).foreach(sessionId => sessionRepository.delete(sessionId))

    authAction.discardingSession {
      Redirect(routes.AuthController.index())
    }
  }

  private def checkPassword(id: String, passwordHash: String): Future[Either[RzError, Account]] =
    accountRepository.getById(id).flatMap {
      case Right(account: Account) =>
        accountRepository.getPassword(account).map {
          case Right(password: String) if HashedString(password).check(passwordHash) => Right(account)
          case _                                                                     => Left(AccessDenied)
        }
      case Left(e) => Future(Left(e))
    }
}

object AuthController {
  val sessionId = "sessionId"

  val userInfoCookie = "accountInfo"

  val sessionName = "accountId"
}
