package controllers

import actions.{ AuthenticatedAction, RepositoryAction }
import models._
import play.api.Configuration
import play.api.mvc._
import repositories.TemplateRepository
import views.html

import collection.immutable._

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

  val createForm: Form[TemplateData] = Form(
    mapping(
      "tplName" -> nonEmptyText
      )
    )(TemplateData.apply)(TemplateData.unapply)

  def list(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, OwnerAccess)).async { implicit req =>
      Future(Ok(html.git.constructor(templateRepository.list)))
    }

  private def getTemplateByName(tplName: String): Option[Template] = ??? // TODO

  private def renderTemplate(ctx: Map[String, Seq[String]], tpl: Template) = ??? // TODO

  

  def create(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, OwnerAccess)).async { implicit req =>
      val ctx = req.body.asFormUrlEncoded.getOrElse(Map[String, Seq[String]]())
      createForm.bindFromRequest.fold(
        formWithErrors => Future(BadRequest(html.git.createRepository(formWithErrors))),
        t => ??? // TODO
      )
      Redirect(
        routes.FileTreeController
          .emptyTree(repo.owner.userName, repository.name, RzRepository.defaultBranch)
      )
    }
}
