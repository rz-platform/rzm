package templates.controllers

import collaborators.models.Role
import documents.controllers.{ RepositoryAction, routes => documentsRoutes }
import documents.models._
import documents.repositories.RzMetaGitRepository
import documents.services.GitService
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc._
import templates.models.{ Template, TemplateDetails }
import templates.repositories.TemplateRepository
import templates.services.TemplateRendererService
import users.controllers.AuthenticatedAction
import views.html

import javax.inject.Inject
import scala.collection.immutable.Map
import scala.concurrent.{ ExecutionContext, Future }
class TemplateController @Inject() (
  authAction: AuthenticatedAction,
  repoAction: RepositoryAction,
  config: Configuration,
  metaGitRepository: RzMetaGitRepository,
  templateRepository: TemplateRepository,
  git: GitService,
  cc: MessagesControllerComponents,
  renderer: TemplateRendererService
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {
  private val logger = play.api.Logger(this.getClass)

  if (templateRepository.list.nonEmpty) {
    renderer.compile(templateRepository.list.values)
  }

  val createForm: Form[TemplateDetails] = Form(
    mapping(
      "name" -> nonEmptyText
    )(TemplateDetails.apply)(TemplateDetails.unapply)
  )

  def view(accountName: String, repositoryName: String, templateId: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Owner)).async { implicit req =>
      templateRepository.list.get(templateId) match {
        case Some(tpl) => Future(Ok(html.repository.creator(templateRepository.list, Some(tpl), Some(tpl.id))))
        case _         => Future(Ok(html.repository.creator(templateRepository.list, None, None)))
      }
    }

  def overview(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Owner)).async { implicit req =>
      templateRepository.list.keySet.headOption match {
        case Some(templateId) =>
          Future(
            Redirect(routes.TemplateController.view(req.repository.owner.username, req.repository.name, templateId))
          )
        case _ => Future(Ok(html.repository.creator(templateRepository.list, None, None)))
      }
    }

  private def flattenMap(context: Map[String, Seq[String]]): Map[String, String] = context.map {
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
      Messages("repository.creator.commit.message", tpl.name)
    )
  }

  private def updateRepoConfig(repo: RzRepository, tpl: Template): Future[Boolean] = {
    val config = RzRepositoryConfig.makeDefault(
      repo,
      tpl.entrypoint,
      RzCompiler.make(tpl.texCompiler),
      RzBib.make(tpl.bibCompiler)
    )
    metaGitRepository.setRzRepoConf(config)
  }

  def build(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Owner)).async { implicit req =>
      val badRequest = Future(BadRequest(html.repository.creator(templateRepository.list, None, None)))
      val success = Redirect(
        documentsRoutes.FileViewController
          .emptyTree(req.repository.owner.username, req.repository.name, RzRepository.defaultBranch)
      )

      val ctx: Map[String, Seq[String]] = req.body.asFormUrlEncoded.getOrElse(Map())
      createForm
        .bindFromRequest()
        .fold(
          _ => badRequest,
          data =>
            templateRepository.get(data.id) match {
              case Some(tpl) =>
                for {
                  _ <- renderTemplate(ctx, tpl)(req)
                  _ <- updateRepoConfig(req.repository, tpl)
                  _ <- metaGitRepository.updateRepo(req.repository)
                } yield success
              case _ => badRequest
            }
        )
    }

  def templatePdf(templateId: String): Action[AnyContent] = Action { implicit request =>
    templateRepository.list.get(templateId) match {
      case Some(tpl) if tpl.illustrationFile.nonEmpty => Ok.sendFile(tpl.illustrationFile.get)

      case _ => NotFound
    }
  }
}
