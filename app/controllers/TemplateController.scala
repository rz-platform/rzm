package controllers

import actions.{ AuthenticatedAction, RepositoryAction }
import play.api.Configuration
import play.api.mvc.MessagesControllerComponents
import repositories.{ AccountRepository, TemplateRepository }

import java.io.{ File, IOException }
import play.api.mvc._

import scala.concurrent.{ ExecutionContext, Future }
import models._
import views.html

import javax.inject.Inject

class TemplateController @Inject() (
  authAction: AuthenticatedAction,
  repoAction: RepositoryAction,
  config: Configuration,
  templateRepository: TemplateRepository,
  cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {
  private val logger = play.api.Logger(this.getClass)

  def list(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, ViewAccess)).async { implicit req =>
      Future(Ok(html.git.constructor(templateRepository.list)))
    }
}
