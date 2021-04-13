package controllers

import actions.{ AuthenticatedAction, RepositoryAction }
import models._
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc._
import repositories.{ GitRepository, TemplateRepository }
import templates.TemplateRenderer
import views.html

import javax.inject.Inject
import scala.collection.immutable.Map
import scala.concurrent.{ ExecutionContext, Future }
class TemplateController @Inject() (
  authAction: AuthenticatedAction,
  repoAction: RepositoryAction,
  config: Configuration,
  templateRepository: TemplateRepository,
  git: GitRepository,
  cc: MessagesControllerComponents,
  renderer: TemplateRenderer
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {
  private val logger = play.api.Logger(this.getClass)

  // TODO: fix
  //  renderer.compile(templateRepository.list.values)

  val createForm: Form[TemplateData] = Form(
    mapping(
      "name" -> nonEmptyText
    )(TemplateData.apply)(TemplateData.unapply)
  )

  def list(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, OwnerAccess)).async { implicit req =>
      Future(Ok(html.git.constructor(templateRepository.list)))
    }

  def flattenMap(context: Map[String, Seq[String]]): Map[String, String] = context.map {
    case (key: String, values: Seq[String]) => (key, values.mkString(""))
  }

  private def renderTemplate(ctx: Map[String, Seq[String]], tpl: Template)(
    implicit req: RepositoryRequest[AnyContent]
  ): Future[_] = Future {
    val files = renderer.render(tpl, flattenMap(ctx))
    git.commitFiles(
      req.repository,
      files,
      req.account,
      RzRepository.defaultBranch,
      ".",
      Messages("repository.constructor.commit.message", tpl.name)
    )
  }

  def build(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, OwnerAccess)).async { implicit req =>
      val badRequest = Future(BadRequest(html.git.constructor(templateRepository.list)))
      val success = Redirect(
        routes.FileTreeController
          .emptyTree(req.repository.owner.userName, req.repository.name, RzRepository.defaultBranch)
      )

      val ctx: Map[String, Seq[String]] = req.body.asFormUrlEncoded.getOrElse(Map())
      createForm
        .bindFromRequest()
        .fold(
          _ => badRequest,
          data =>
            templateRepository.get(data.name) match {
              case Some(tpl) => renderTemplate(ctx, tpl)(req).map(_ => success)
              case _         => badRequest
            }
        )
    }
}
