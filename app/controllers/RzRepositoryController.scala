package controllers

import actions.{ AuthenticatedAction, RepositoryAction }
import forms.RzRepositoryForms.createRepositoryForm
import forms.{ FormErrors, RzRepositoryForms }
import models._
import play.api.data.FormError
import play.api.i18n.Messages
import play.api.mvc.{ Action, AnyContent, MessagesAbstractController, MessagesControllerComponents }
import repositories.RzMetaGitRepository
import services.GitService
import views.html

import java.io.File
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class RzRepositoryController @Inject() (
  git: GitService,
  metaGitRepository: RzMetaGitRepository,
  authAction: AuthenticatedAction,
  repoAction: RepositoryAction,
  cc: MessagesControllerComponents,
  errorHandler: ErrorHandler
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  def createPage: Action[AnyContent] = authAction.async { implicit req =>
    Future(Ok(html.createRepository(createRepositoryForm)))
  }

  def save: Action[AnyContent] = authAction.async { implicit req =>
    val cleanData = RzRepositoryForms.clear(req.body.asFormUrlEncoded)
    createRepositoryForm
      .bindFromRequest(cleanData)
      .fold(
        formWithErrors => Future(BadRequest(html.createRepository(formWithErrors))),
        repository =>
          metaGitRepository.getByOwnerAndName(req.account.username, repository.name).flatMap {
            case Left(NotFoundInRepository) =>
              val repo   = new RzRepository(req.account, repository.name)
              val author = new Collaborator(req.account, Role.Owner)
              val conf   = RzRepositoryConfig.makeDefault(repo, None, None, None)
              for {
                _ <- metaGitRepository.createRepo(repo, author, conf)
                _ <- git.initRepo(repo)
              } yield Redirect(
                routes.TemplateController.overview(req.account.username, repo.name)
              )
            case _ =>
              val newForm = FormErrors.error[RepositoryData](
                createRepositoryForm.bindFromRequest(),
                FormError("name", Messages("repository.create.error.alreadyexists"))
              )
              Future(BadRequest(html.createRepository(newForm)))
          }
      )
  }

  /**
   * Display list of repositories.
   */
  def list: Action[AnyContent] = authAction.async { implicit req =>
    for {
      list <- metaGitRepository.listRepositories(req.account)
    } yield Ok(html.listRepositories(list))
  }

  def downloadArchive(
    accountName: String,
    repositoryName: String,
    rev: String
  ): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Viewer)).async { implicit req =>
      for {
        file: File <- git.createArchive(req.repository, "", rev)
      } yield Ok.sendFile(
        content = file,
        inline = false,
        fileName = _ => Some(repositoryName + "-" + rev + ".zip")
      )
    }

  def commitLog(accountName: String, repositoryName: String, rev: String, page: Int): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Viewer)).async { implicit req =>
      for {
        log <- git.commitsLog(req.repository, rev, page, 20)
      } yield {
        val (logs, hasNext) = log match {
          case Right((logs, hasNext)) => (logs, hasNext)
          case Left(_)                => (Seq(), false)
        }
        Ok(html.repository.log(logs, rev, hasNext, page))
      }
    }
}
