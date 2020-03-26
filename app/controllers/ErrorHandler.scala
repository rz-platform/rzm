package controllers

import javax.inject.{Inject, Singleton}
import play.api.http.HttpErrorHandler
import play.api.mvc.Results._
import play.api.mvc._
import play.api.i18n.Messages.Implicits._
import play.api.i18n.MessagesApi

import scala.concurrent._

@Singleton
class ErrorHandler @Inject() (val messagesApi: MessagesApi) extends HttpErrorHandler with play.api.i18n.I18nSupport {
  private val logger = play.api.Logger(this.getClass)

  def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    Future.successful(
      Status(statusCode)(views.html.error(request))
    )
  }

  def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    Future.successful(
      InternalServerError("A server error occurred: " + exception.getMessage)
    )
  }
}
