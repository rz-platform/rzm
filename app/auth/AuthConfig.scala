package auth

import play.api.{ Environment, Mode }
import play.api.cache.CacheApi
import play.api.mvc._

import scala.reflect.{ ClassTag, classTag }
import scala.concurrent.{ ExecutionContext, Future }

trait AuthConfig {

  type Id

  type User

  type Authority

  val environment: Environment

  val idContainer: AsyncIdContainer[Id]

  implicit def idTag: ClassTag[Id]

  def sessionTimeoutInSeconds: Int

  def resolveUser(id: Id)(implicit context: ExecutionContext): Future[Option[User]]

  def loginSucceeded(request: RequestHeader)(implicit context: ExecutionContext): Future[Result]

  def logoutSucceeded(request: RequestHeader)(implicit context: ExecutionContext): Future[Result]

  def authenticationFailed(request: RequestHeader)(implicit context: ExecutionContext): Future[Result]

  def authorizationFailed(request: RequestHeader, user: User, authority: Option[Authority])(implicit context: ExecutionContext): Future[Result]

  def authorize(user: User, authority: Authority)(implicit context: ExecutionContext): Future[Boolean]

  lazy val tokenAccessor: TokenAccessor = new CookieTokenAccessor(
    cookieName = "PLAY2AUTH_SESS_ID",
    cookieSecureOption = environment.mode == Mode.Prod,
    cookieHttpOnlyOption = true,
    cookieDomainOption = None,
    cookiePathOption = "/",
    cookieMaxAge = Some(sessionTimeoutInSeconds)
  )

}
