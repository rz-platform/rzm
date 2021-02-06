package controllers

import actions.AuthenticatedAction
import encryption.{ EncryptionService, UserInfoCookieBakerFactory }
import models._
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.Messages
import play.api.mvc._
import repositories.{ AccessDenied, AccountRepository, RzError, SessionRepository }
import views.html

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class AuthController @Inject() (
  accountRepository: AccountRepository,
  encryptionService: EncryptionService,
  sessionRepository: SessionRepository,
  authAction: AuthenticatedAction,
  factory: UserInfoCookieBakerFactory,
  cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  private def createSession(userInfo: UserInfo): Future[(String, Cookie)] = {
    // create a user info cookie with this specific secret key
    val secretKey      = encryptionService.newSecretKey
    val cookieBaker    = factory.createCookieBaker(secretKey)
    val userInfoCookie = cookieBaker.encodeAsCookie(Some(userInfo))

    // Tie the secret key to a session id, and store the encrypted data in client side cookie
    sessionRepository.create(secretKey).map(sessionId => (sessionId, userInfoCookie))
  }

  def loginPage: Action[AnyContent] = Action.async(implicit request => Future(Ok(html.signin(form))))

  val form: Form[AccountLoginData] = Form(
    mapping("userName" -> nonEmptyText, "password" -> nonEmptyText)(AccountLoginData.apply)(AccountLoginData.unapply)
  )

  def index: Action[AnyContent] =
    authAction.async(implicit req => Future(Redirect(routes.RzRepositoryController.list())))

  private def checkPassword(userName: String, passwordHash: String): Future[Either[RzError, Account]] =
    accountRepository.getByUsernameOrEmail(userName).flatMap {
      case Right(account: Account) =>
        accountRepository.getPassword(account).map {
          case Right(password: String) if HashedString(password).check(passwordHash) => Right(account)
          case _                                                                     => Left(AccessDenied)
        }
      case Left(e) => Future(Left(e))
    }

  def login = Action.async { implicit req =>
    val proceed: Account => Future[Result] = { acc: Account =>
      val userInfo = UserInfo(acc.userName)
      createSession(userInfo).map {
        case (sessionId, encryptedCookie) =>
          val session = req.session + (Auth.SESSION_ID -> sessionId)
          Redirect(routes.RzRepositoryController.list())
            .withSession(session)
            .withCookies(encryptedCookie)
      }
    }

    val successFunc: AccountLoginData => Future[Result] = { accountData: AccountLoginData =>
      checkPassword(accountData.userName, accountData.password).flatMap {
        case Right(account) => proceed(account)
        case _ =>
          val formBuiltFromRequest = form.bindFromRequest
          val newForm = form.bindFromRequest.copy(
            errors = formBuiltFromRequest.errors ++ Seq(FormError("userName", Messages("signin.error.wrongcred")))
          )
          Future(BadRequest(html.signin(newForm)))
      }
    }

    val errorFunc: Form[AccountLoginData] => Future[Result] = { badForm: Form[AccountLoginData] =>
      Future.successful {
        BadRequest(html.signin(badForm))
      }
    }

    form.bindFromRequest().fold(errorFunc, successFunc)
  }

//  def discardingSession(result: Result): Result =
//    result.withNewSession.discardingCookies(DiscardingCookie(Auth.USER_INFO_COOKIE_NAME))

  def logout: Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    // When we delete the session id, removing the session id is enough to render the
    // user info cookie unusable.
    req.session.get(Auth.SESSION_ID).foreach(sessionId => sessionRepository.delete(sessionId))

    authAction.discardingSession {
      Redirect(routes.AuthController.index())
    }
  }

}
