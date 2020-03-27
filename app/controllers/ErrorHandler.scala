package controllers

import javax.inject.Singleton
import play.api.http.DefaultHttpErrorHandler
import play.api.mvc.Results._
import play.api.mvc._

import scala.concurrent._

@Singleton
class ErrorHandler extends DefaultHttpErrorHandler {
  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] =
    Future.successful {
      implicit val ir: RequestHeader = request
      Status(statusCode)(views.html.error(message))
    }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] =
    Future.successful {
      InternalServerError("A server error occurred: " + exception.getMessage)
    }
}
