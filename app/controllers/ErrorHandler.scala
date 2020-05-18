package controllers

import javax.inject._
import play.api._
import play.api.http.DefaultHttpErrorHandler
import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing.Router

import scala.concurrent._

@Singleton
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

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] =
    if (env.mode == Mode.Prod) {
      Future.successful {
        implicit val ir: RequestHeader = request
        BadRequest(views.html.error(message))
      }
    } else {
      super.onClientError(request, statusCode, message)
    }
}
