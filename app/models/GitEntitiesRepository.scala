package models

import java.io.{ File, InputStream }
import java.util.Date

import anorm.SqlParser.get
import anorm._
import javax.inject.{ Inject, Singleton }
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import play.api.db.DBApi

import scala.concurrent.Future

case class Repository(
  id: Long,
  owner: Account,
  name: String,
  defaultBranch: String
)

object Repository {
  implicit def toParameters: ToParameterList[Repository] = Macro.toParameters[Repository]
  val defaultBranchName                                  = "master"
}

case class RepositoryData(name: String, description: Option[String])

case class Collaborator(
  id: Long,
  userId: Long,
  repositoryId: Long,
  role: Long
)

case class NewCollaboratorData(emailOrLogin: String, accessLevel: String)

case class RemoveCollaboratorData(email: String)

case class CommitFile(id: String, name: String, file: File)

case class EditedItem(content: String, message: String, rev: String, path: String, fileName: String)

case class UploadFileForm(path: String, message: String)

/**
 * The file data for the file list of the repository viewer.
 *
 * @param id          the object id
 * @param isDirectory whether is it directory
 * @param name        the file (or directory) name
 * @param path        the file (or directory) complete path
 * @param message     the last commit message
 * @param commitId    the last commit id
 * @param time        the last modified time
 * @param author      the last committer name
 * @param mailAddress the committer's mail address
 * @param linkUrl     the url of submodule
 */
case class FileInfo(
  id: ObjectId,
  isDirectory: Boolean,
  name: String,
  path: String,
  message: String,
  commitId: String,
  time: Date,
  author: String,
  mailAddress: String,
  linkUrl: Option[String]
)

case class RepositoryGitData(files: List[FileInfo], lastCommit: Option[RevCommit])

case class NewItem(name: String, rev: String)

/**
 * The file content data for the file content view of the repository viewer.
 *
 * @param viewType "image", "large" or "other"
 * @param size     total size of object in bytes
 * @param content  the string content
 * @param charset  the character encoding
 */
case class ContentInfo(viewType: String, size: Option[Long], content: Option[String], charset: Option[String]) {

  /**
   * the line separator of this content ("LF" or "CRLF")
   */
  val lineSeparator: String = if (content.exists(_.indexOf("\r\n") >= 0)) "CRLF" else "LF"
}

case class Blob(
  content: ContentInfo,
  latestCommit: CommitInfo,
  isLfsFile: Boolean
)

case class PathBreadcrumb(name: String, path: String)

case class RawFile(inputStream: InputStream, contentLength: Integer, contentType: String)

/**
 * The commit data.
 *
 * @param id                    the commit id
 * @param shortMessage          the short message
 * @param fullMessage           the full message
 * @param parents               the list of parent commit id
 * @param authorTime            the author time
 * @param authorName            the author name
 * @param authorEmailAddress    the mail address of the author
 * @param commitTime            the commit time
 * @param committerName         the committer name
 * @param committerEmailAddress the mail address of the committer
 */
case class CommitInfo(
  id: String,
  shortMessage: String,
  fullMessage: String,
  parents: List[String],
  authorTime: Date,
  authorName: String,
  authorEmailAddress: String,
  commitTime: Date,
  committerName: String,
  committerEmailAddress: String
) {

  def this(rev: org.eclipse.jgit.revwalk.RevCommit) =
    this(
      rev.getName,
      rev.getShortMessage,
      rev.getFullMessage,
      rev.getParents.map(_.name).toList,
      rev.getAuthorIdent.getWhen,
      rev.getAuthorIdent.getName,
      rev.getAuthorIdent.getEmailAddress,
      rev.getCommitterIdent.getWhen,
      rev.getCommitterIdent.getName,
      rev.getCommitterIdent.getEmailAddress
    )

  def isDifferentFromAuthor: Boolean = authorName != committerName || authorEmailAddress != committerEmailAddress
}

object Collaborator {
  implicit def toParameters: ToParameterList[Collaborator] =
    Macro.toParameters[Collaborator]
}

@Singleton
class GitEntitiesRepository @Inject() (accountRepository: AccountRepository, dbapi: DBApi)(
  implicit ec: DatabaseExecutionContext
) {
  private val db = dbapi.database("default")

  /**
   * Parse a Repository from a ResultSet
   */
  private val simple = {
    (get[Long]("repository.id") ~
      accountRepository.simple ~
      get[String]("repository.name") ~
      get[String]("repository.default_branch")).map {
      case id ~ owner ~ name ~ defaultBranch =>
        Repository(id, owner, name, defaultBranch)
    }
  }

  def getByAuthorAndName(owner: String, repoName: String): Future[Option[Repository]] =
    Future {
      db.withConnection { implicit connection =>
        SQL("""
        select repository.id, repository.name, repository.default_branch,
        account.id, account.username, account.has_picture, account.email
        from repository
        join account on repository.owner_id = account.id
        where
        account.username = {owner} and
        repository.name = {repoName}
      """).on("owner" -> owner, "repoName" -> repoName).as(simple.singleOpt)
      }
    }(ec)

  def getIdByAuthorAndName(author: String, repName: String): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL"""
        select repository.id from repository
        join account on repository.owner_id = account.id
        where
        repository.name = {repoName} and
        account.username = {owner}
      """.as(SqlParser.int("repository.id").single)

      }
    }(ec)

  def createCollaborator(repositoryId: Long, collaboratorId: Long, role: Int): Future[Option[Long]] = Future {
    db.withConnection { implicit connection =>
      SQL("""
        insert into collaborator (user_id, repository_id, role) values ({userId}, {repositoryId}, {role})
      """).on("userId" -> collaboratorId, "repositoryId" -> repositoryId, "role" -> role).executeInsert()
    }
  }

  def removeCollaborator(repositoryId: Long, collaboratorId: Long): Future[Int] = Future {
    db.withConnection { implicit connection =>
      SQL("""
        DELETE FROM collaborator WHERE user_id={userId} and repository_id={repositoryId}
      """).on("userId" -> collaboratorId, "repositoryId" -> repositoryId).executeUpdate()
    }
  }

  /**
   * Insert a new repository
   *
   */
  def insertRepository(ownerId: Long, repository: RepositoryData): Future[Option[Long]] =
    Future {
      db.withConnection { implicit connection =>
        SQL("""
        insert into repository (name, owner_id, description, default_branch) values (
          {name},{owner_id},{description},{defaultBranch}
        )
      """).on(
            "name"          -> repository.name,
            "owner_id"      -> ownerId,
            "description"   -> repository.description.getOrElse(""),
            "defaultBranch" -> Repository.defaultBranchName
          )
          .executeInsert()
      }
    }(ec)

  def isUserCollaborator(repository: Repository, userId: Long): Future[Option[Int]] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""
        select role from collaborator
        where collaborator.user_id = {collaboratorId} and repository_id = {repositoryId}
          """)
          .on("collaboratorId" -> userId, "repositoryId" -> repository.id)
          .as(SqlParser.int("role").singleOpt)
      }
    }(ec)

  def getCollaborators(repository: Repository): Future[List[Account]] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""
        select * from collaborator
        join account
        on account.id = collaborator.user_id
        where repository_id = {repositoryId}
        """)
          .on("repositoryId" -> repository.id)
          .as(accountRepository.simple.*)
      }
    }(ec)

  def listRepositories(accountId: Long): Future[List[Repository]] =
    Future {
      db.withConnection { implicit connection =>
        SQL"""
          select repository.id, repository.name, repository.default_branch,
          account.id, account.username, account.has_picture, account.email
          from repository
          join account on repository.owner_id = account.id
          left join collaborator on repository.id = collaborator.repository_id
          where repository.owner_id = $accountId
          or (collaborator.user_id = $accountId)
      """.as(simple.*)
      }
    }(ec)
}
