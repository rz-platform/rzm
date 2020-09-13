package controllers

import javax.inject._
import play.api._
import play.api.http.DefaultHttpErrorHandler
import play.api.http.Status._
import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing.Router

import scala.concurrent._

/**
 * Provides an error handler that uses HTML template in error pages in Prod environment
 *
 * https://www.playframework.com/documentation/latest/ScalaErrorHandling
 */
class ErrorHandler @Inject() (
  env: Environment,
  config: Configuration,
  sourceMapper: OptionalSourceMapper,
  router: Provider[Router]
) extends DefaultHttpErrorHandler(env, config, sourceMapper, router) {
  override def onProdServerError(request: RequestHeader, exception: UsefulException): Future[Result] =
    Future.successful {
      implicit val ir: RequestHeader = request
      BadRequest(views.html.error(exception.getMessage))
    }

  override def onClientError(request: RequestHeader, statusCode: Int = BAD_REQUEST, msg: String): Future[Result] =
    if (env.mode == Mode.Prod) {
      Future.successful {
        implicit val ir: RequestHeader = request
        BadRequest(views.html.error(msg))
      }
    } else {
      super.onClientError(request, statusCode, msg)
    }

  /**
   *
   * Simple handler for cases when we need just Result
   *
   */
  def clientError(request: RequestHeader, msg: String): Result = {
    implicit val ir: RequestHeader = request
    BadRequest(views.html.error(msg))
  }
}
