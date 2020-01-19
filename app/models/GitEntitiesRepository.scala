package models

import java.util.Date
import java.io.File
import javax.inject.Inject
import play.api.db.DBApi
import anorm._
import anorm.SqlParser.{get, str}
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit

import scala.concurrent.Future

case class Repository(
                       id: Long,
                       name: String,
                       isPrivate: Boolean,
                       description: Option[String],
                       defaultBranch: String,
                       registeredDate: java.util.Date,
                       lastActivityDate: java.util.Date
                     )

case class RepositoryData(
                           name: String,
                           description: Option[String],
                         )

case class Collaborator(
                         id: Long,
                         userId: Long,
                         repositoryId: Long,
                         role: Long
                       )

case class NewCollaboratorData(
                                emailOrLogin: String,
                                accessLevel: String
                              )

case class CommitFile(id: String, name: String, file: File)

case class EditedItem(content: String, message: String, newFileName: Option[String], oldFileName: String)

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

case class NewItem(name: String)

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

case class RepositoryWithOwner(repository: Repository, owner: Account)

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
      rev.getParents().map(_.name).toList,
      rev.getAuthorIdent.getWhen,
      rev.getAuthorIdent.getName,
      rev.getAuthorIdent.getEmailAddress,
      rev.getCommitterIdent.getWhen,
      rev.getCommitterIdent.getName,
      rev.getCommitterIdent.getEmailAddress
    )

  def isDifferentFromAuthor: Boolean = authorName != committerName || authorEmailAddress != committerEmailAddress
}


object Repository {
  implicit def toParameters: ToParameterList[Repository] =
    Macro.toParameters[Repository]
}

object Collaborator {
  implicit def toParameters: ToParameterList[Collaborator] =
    Macro.toParameters[Collaborator]
}

@javax.inject.Singleton
class GitEntitiesRepository @Inject()(accountRepository: AccountRepository,
                                      dbapi: DBApi)
                                     (implicit ec: DatabaseExecutionContext) {
  private val db = dbapi.database("default")

  /**
    * Parse a Repository from a ResultSet
    */
  private val simple = {
    get[Long]("repository.id") ~
      get[String]("repository.name") ~
      get[Boolean]("repository.isPrivate") ~
      get[Option[String]]("repository.description") ~
      get[String]("repository.defaultBranch") ~
      get[Date]("repository.registeredDate") ~
      get[Date]("repository.lastActivityDate") map {
      case id ~ name ~ isPrivate ~ description ~ defaultBranch ~ registeredDate ~ lastActivityDate =>
        Repository(id, name, isPrivate, description, defaultBranch, registeredDate, lastActivityDate)
    }
  }

  /**
    * Parse a (Computer,Company) from a ResultSet
    */
  private val withOwner = simple ~ accountRepository.simple map {
    case repository ~ account => RepositoryWithOwner(repository, account)// repository -> account
  }

  def getByAuthorAndName(author: String, repName: String): Future[Option[RepositoryWithOwner]] = Future {
    db.withConnection { implicit connection =>
      SQL"""
        select * from repository
        join collaborator
        on collaborator.repositoryId = repository.id
        join account
        on account.id = collaborator.userid
        where repository.name = $repName
        and account.username = $author
        and collaborator.role = ${AccessLevel.owner}
      """
        .as(withOwner.singleOpt)

    }
  }(ec)

  def getIdByAuthorAndName(author: String, repName: String): Future[Int] = Future {
    db.withConnection { implicit connection =>
      SQL"""
        select repository.id from repository
        join collaborator
        on collaborator.repositoryId = repository.id
        join account
        on account.id = collaborator.userid
        where repository.name = $repName
        and account.username = $author
        and collaborator.role = ${AccessLevel.owner}
      """
        .as(SqlParser.int("repository.id").single)

    }
  }(ec)

  def createCollaborator(repositoryId: Long, collaboratorId: Long, role: Int): Future[Option[Long]] = Future {
    db.withConnection { implicit connection =>
      SQL(
        """
        insert into collaborator (userId, repositoryId, role) values ({userId}, {repositoryId}, {role})
      """).on("userId" -> collaboratorId, "repositoryId" -> repositoryId, "role" -> role).executeInsert()
    }
  }

  /**
    * Insert a new repository
    *
    */
  def insertRepository(repository: Repository): Future[Option[Long]] = Future {
    db.withConnection { implicit connection =>
      SQL(
        """
        insert into repository (name, isPrivate, description, defaultBranch, registeredDate, lastActivityDate) values (
          {name},{isPrivate},{description},{defaultBranch},{registeredDate}, {lastActivityDate}
        )
      """).bind(repository).executeInsert()
    }
  }(ec)

  def isUserCollaborator(repository: Repository, userId: Long): Future[Option[Int]] = Future {
    db.withConnection { implicit connection =>
      SQL(
        s"""select role from collaborator
        where collaborator.userId = {collaboratorId}
        and repositoryId = {repositoryId}
          """)
        .on("collaboratorId" -> userId, "repositoryId" -> repository.id)
        .as(SqlParser.int("role").singleOpt)
    }
  }(ec)

  def getCollaborators(repository: Repository): Future[List[Account]] = Future {
    db.withConnection { implicit connection =>
      SQL(
        s"""
        select * from collaborator
        join account
        on account.id = collaborator.userId
        where repositoryId = {repositoryId}
        """)
        .on("repositoryId" -> repository.id)
        .as(accountRepository.simple.*)
    }
  }(ec)

  def listRepositories(accountId: Long): Future[List[RepositoryWithOwner]] = Future {
    db.withConnection { implicit connection =>
      SQL"""
        select * from (
          select * from repository
          join collaborator
          on collaborator.repositoryId = repository.id
          and collaborator.userId = $accountId) as availableRepositories, collaborator
        join account
        on account.id = collaborator.userid
        where collaborator.role = ${AccessLevel.owner}
      """.as(withOwner.*)
    }
  }(ec)
}