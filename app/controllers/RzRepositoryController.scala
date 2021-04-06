package controllers

import actions.{ AuthenticatedAction, RepositoryAction }
import models._
import play.api.data.Forms.{ mapping, nonEmptyText, optional, text }
import play.api.data.validation.Constraints.pattern
import play.api.data.{ Form, FormError }
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

  val createRepositoryForm: Form[RepositoryData] = Form(
    mapping(
      "name"        -> nonEmptyText(minLength = 1, maxLength = 36).verifying(pattern(RzRegex.onlyAlphabet)),
      "description" -> optional(text(maxLength = 255))
    )(RepositoryData.apply)(RepositoryData.unapply)
  )

  def createRepository: Action[AnyContent] = authAction.async { implicit req =>
    Future(Ok(html.git.createRepository(createRepositoryForm)))
  }

  private def clearRepositoryData(data: Option[Map[String, Seq[String]]]): Map[String, Seq[String]] = {
    val form: Map[String, Seq[String]] = data.getOrElse(collection.immutable.Map[String, Seq[String]]())
    form.map {
      case (key, values) if key == "name"        => (key, values.map(_.trim.toLowerCase()))
      case (key, values) if key == "description" => (key, values.map(_.trim))
      case (key, values)                         => (key, values)
    }
  }

  def saveRepository: Action[AnyContent] = authAction.async { implicit req =>
    val cleanData = clearRepositoryData(req.body.asFormUrlEncoded)
    createRepositoryForm
      .bindFromRequest(cleanData)
      .fold(
        formWithErrors => Future(BadRequest(html.git.createRepository(formWithErrors))),
        repository =>
          metaGitRepository.getByOwnerAndName(req.account.userName, repository.name).flatMap {
            case Left(NotFoundInRepository) =>
              val repo   = new RzRepository(req.account, repository.name)
              val author = new Collaborator(req.account, OwnerAccess)
              metaGitRepository.setRzRepo(repo, author)
              git.create(repo)
              Future(
                Redirect(
                  routes.FileTreeController
                    .emptyTree(repo.owner.userName, repository.name, RzRepository.defaultBranch)
                )
              )
            case _ =>
              val formBuiltFromRequest = createRepositoryForm.bindFromRequest
              val newForm = createRepositoryForm.bindFromRequest.copy(
                errors = formBuiltFromRequest.errors ++ Seq(
                  FormError("name", Messages("repository.create.error.alreadyexists"))
                )
              )
              Future(BadRequest(html.git.createRepository(newForm)))
          }
      )
  }

  /**
   * Display list of repositories.
   */
  def list: Action[AnyContent] = authAction.async { implicit req =>
    metaGitRepository.listRepositories(req.account).flatMap(list => Future(Ok(html.git.listRepositories(list))))
  }

  def downloadRepositoryArchive(
    accountName: String,
    repositoryName: String,
    rev: String
  ): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, ViewAccess)) { implicit req =>
      Ok.sendFile(
        git.createArchive(req.repository, "", rev),
        inline = false,
        fileName = _ => Some(repositoryName + "-" + rev + ".zip")
      )
    }

  def commitLog(accountName: String, repositoryName: String, rev: String, page: Int): Action[AnyContent] =
    authAction.andThen(repoAction.on(accountName, repositoryName, ViewAccess)).async { implicit req =>
      val commitLog = git.getCommitsLog(req.repository, rev, page, 30)
      commitLog match {
        case Right((logs, hasNext)) => Future(Ok(html.git.commitLog(logs, rev, hasNext, page)))
        case Left(_)                => errorHandler.onClientError(req, msg = Messages("error.notfound"))
      }
    }
}
