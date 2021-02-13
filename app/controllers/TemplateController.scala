package controllers

import actions.{ AuthenticatedAction, RepositoryAction }
import play.api.Configuration
import play.api.mvc.MessagesControllerComponents
import repositories.AccountRepository
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
  cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {
  private val logger = play.api.Logger(this.getClass)
  
  private val templatesDir = new File(config.get[String]("play.server.templates.dir"))

  def templateList: Array[Template] = {
  	if (templatesDir.exists && templatesDir.isDirectory) {
        templatesDir.listFiles.filter(!_.isFile).map(file => Template(file.getName))
    } else {
        Array[Template]()
    }
  }

  def list(accountName: String, repositoryName: String): Action[AnyContent] = 
  	authAction.andThen(repoAction.on(accountName, repositoryName, ViewAccess)).async { implicit req =>
    	Future(Ok(html.git.constructor(templateList)))
  }
}
