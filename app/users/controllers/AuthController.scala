package users.controllers

import authentication.models.{ EmailAndPasswordCredentials, Password }
import authentication.repositories.SessionRepository
import documents.controllers.{ routes => documentsRoutes }
import infrastructure.errors.{ AccessDenied, RepositoryError }
import infrastructure.validations.FormErrors
import play.api.data._
import play.api.i18n.Messages
import play.api.mvc._
import users.models.{ Account, AccountInfo }
import users.repositories.AccountRepository
import users.validations.AuthForms
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

  private val logger = play.api.Logger(this.getClass)

  def loginPage: Action[AnyContent] = Action.async(implicit request => Future(Ok(html.signin(AuthForms.signin))))

  def index: Action[AnyContent] =
    authAction.async(implicit req => Future(Redirect(documentsRoutes.RzRepositoryController.list())))

  def login: Action[AnyContent] = Action.async { implicit req =>
    val successFunc: EmailAndPasswordCredentials => Future[Result] = { accountData =>
      checkPassword(accountData.username, accountData.password).flatMap {
        case Right(account) =>
          for {
            (sessionId, encryptedCookie) <- authAction.createSession(AccountInfo(account.id))
            _                            <- accountRepository.setTimezone(account, accountData.timezone)
          } yield {
            val session = req.session + (AuthController.sessionId -> sessionId)
            Redirect(documentsRoutes.RzRepositoryController.list())
              .withSession(session)
              .withCookies(encryptedCookie)
          }
        case _ =>
          val newForm = FormErrors.error[EmailAndPasswordCredentials](
            AuthForms.signin.bindFromRequest(),
            FormError("username", Messages("signin.error.wrongcred"))
          )
          Future(BadRequest(html.signin(newForm)))
      }
    }

    val errorFunc: Form[EmailAndPasswordCredentials] => Future[Result] = { badForm: Form[EmailAndPasswordCredentials] =>
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

  private def checkPassword(nameOrEmail: String, password: String): Future[Either[RepositoryError, Account]] =
    accountRepository.getByUsernameOrEmail(nameOrEmail).flatMap {
      case Right(account: Account) =>
        accountRepository.getPassword(account).map {
          case Right(passwordHash: String) if Password(passwordHash).check(password) => Right(account)
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
