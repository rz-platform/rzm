package repositories

import anorm.SqlParser.get
import anorm._
import javax.inject.{ Inject, Singleton }
import models.{ Collaborator, Repository, RepositoryData, SimpleAccount }
import play.api.db.DBApi

import scala.concurrent.Future

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
      case id ~ owner ~ name ~ defaultBranch => Repository(id, owner, name, defaultBranch)
    }
  }

  /**
   * Parse a Collaborator from a ResultSet
   */
  private val simpleCollaborator = {
    (accountRepository.simple ~ get[Int]("collaborator.role")).map {
      case account ~ role => Collaborator(account, role)
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

  def getCollaborators(repository: Repository): Future[List[Collaborator]] =
    Future {
      db.withConnection { implicit connection =>
        SQL(s"""
        select * from collaborator
        join account
        on account.id = collaborator.user_id
        where repository_id = {repositoryId}
        order by collaborator.id asc
        """)
          .on("repositoryId" -> repository.id)
          .as(simpleCollaborator.*)
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
          order by repository.id desc
      """.as(simple.*)
      }
    }(ec)
}
