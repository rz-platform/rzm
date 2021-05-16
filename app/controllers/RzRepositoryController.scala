package controllers

import actions.{ AuthenticatedAction, RepositoryAction }
import forms.RzRepositoryForms
import forms.RzRepositoryForms.createRepositoryForm
import models._
import play.api.data.FormError
import play.api.i18n.Messages
import play.api.mvc.{ Action, AnyContent, MessagesAbstractController, MessagesControllerComponents }
import repositories.{ GitRepository, NotFoundInRepository, RzMetaGitRepository }
import views.html

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class RzRepositoryController @Inject() (
  git: GitRepository,
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
          metaGitRepository.getByOwnerAndName(req.account.userName, repository.name).flatMap {
            case Left(NotFoundInRepository) =>
              val repo   = new RzRepository(req.account, repository.name)
              val author = new Collaborator(req.account, Role.Owner)
              val conf   = RzRepositoryConfig.makeDefault(repo, None, None, None)
              metaGitRepository.setRzRepo(repo, author, conf)
              git.initRepo(repo)
              Future(
                Redirect(
                  routes.TemplateController.overview(req.account.userName, repo.name)
                )
              )
            case _ =>
              val formBuiltFromRequest = createRepositoryForm.bindFromRequest()
              val newForm = createRepositoryForm
                .bindFromRequest()
                .copy(
                  errors = formBuiltFromRequest.errors ++ Seq(
                    FormError("name", Messages("repository.create.error.alreadyexists"))
                  )
                )
              Future(BadRequest(html.createRepository(newForm)))
          }
      )
  }

  /**
   * Display list of repositories.
   */
  def list: Action[AnyContent] = authAction.async { implicit req =>
    metaGitRepository.listRepositories(req.account).flatMap(list => Future(Ok(html.listRepositories(list))))
  }

  def downloadArchive(
    accountName: String,
    repositoryName: String,
    rev: String
  ): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Viewer)) { implicit req =>
      Ok.sendFile(
        git.createArchive(req.repository, "", rev),
        inline = false,
        fileName = _ => Some(repositoryName + "-" + rev + ".zip")
      )
    }

  def commitLog(accountName: String, repositoryName: String, rev: String, page: Int): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, Role.Viewer)).async { implicit req =>
      val commitLog = git.getCommitsLog(req.repository, rev, page, 20)
      val (logs, hasNext) = commitLog match {
        case Right((logs, hasNext)) => (logs, hasNext)
        case Left(_)                => (Seq(), false)
      }
      Future(Ok(html.repository.log(logs, rev, hasNext, page)))
    }
}
