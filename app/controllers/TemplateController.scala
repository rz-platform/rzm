package controllers

import actions.{ AuthenticatedAction, RepositoryAction }
import models._
import play.api.Configuration
import play.api.mvc._
import repositories.TemplateRepository
import views.html

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

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
