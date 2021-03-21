package controllers

import actions.{ AuthenticatedAction, RepositoryAction }
import models._
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc._
import repositories.{ GitRepository, TemplateRepository }
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
  cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {
  private val logger = play.api.Logger(this.getClass)

  val createForm: Form[TemplateData] = Form(
    mapping(
      "name" -> nonEmptyText
    )(TemplateData.apply)(TemplateData.unapply)
  )

  def list(accountName: String, repositoryName: String): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, OwnerAccess)).async { implicit req =>
      Future(Ok(html.git.constructor(templateRepository.list)))
    }

  private def renderTemplate(ctx: Map[String, Seq[String]], tpl: Template)(
    implicit req: RepositoryRequest[AnyContent]
  ) = Future {
    val files = tpl.path.listFiles
      .filter(_.isDirectory)
      .map { f =>
        val filePath = RzPathUrl.make(".", f.getName, isFolder = false).uri
        CommitFile(f.getName, filePath, f)
      }
      .toSeq
    git.commitUploadedFiles(
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

      val ctx: Map[String, Seq[String]] = req.body.asFormUrlEncoded.getOrElse(Map())
      createForm.bindFromRequest.fold(
        _ => badRequest,
        data => {
          Messages("repository.constructor.commit.message", data.name)
          templateRepository.get(data.name) match {
            case Some(tpl) =>
              renderTemplate(ctx, tpl)(req).map { _ =>
                Redirect(
                  routes.FileTreeController
                    .emptyTree(req.repository.owner.userName, req.repository.name, RzRepository.defaultBranch)
                )
              }
            case _ => badRequest
          }
        }
      )
    }
}
