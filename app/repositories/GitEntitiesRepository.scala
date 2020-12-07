package repositories

import anorm.SqlParser.get
import anorm._
import javax.inject.{ Inject, Singleton }
import models.{ Collaborator, RepositoryData, RzRepository }
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
  private val simpleGitRepositoryParser = {
    (get[Int]("repository.id") ~
      accountRepository.simpleAccountParser ~
      get[String]("repository.name") ~
      get[String]("repository.default_branch") ~
      get[Option[String]]("repository.main_file")).map {
      case id ~ owner ~ name ~ defaultBranch ~ mainFile => RzRepository(id, owner, name, defaultBranch, mainFile)
    }
  }

  /**
   * Parse a Collaborator from a ResultSet
   */
  private val collaboratorParser = {
    (accountRepository.simpleAccountParser ~ get[Int]("collaborator.role")).map {
      case account ~ role => Collaborator(account, role)
    }
  }

  def getByOwnerAndName(owner: String, repoName: String): Future[Option[RzRepository]] =
    Future {
      db.withConnection { implicit connection =>
        SQL("""
        select repository.id, repository.name, repository.default_branch, repository.main_file,
        account.id, account.username, account.has_picture, account.email
        from repository
        join account on repository.owner_id = account.id
        where
        account.username = {owner} and
        repository.name = {repoName}
      """).on("owner" -> owner, "repoName" -> repoName)
          .as(simpleGitRepositoryParser.singleOpt)
      }
    }(ec)

  def getIdByAuthorAndName(author: String, repName: String): Future[Int] =
    Future {
      db.withConnection { implicit connection =>
        SQL("""
          select repository.id from repository
          join account on repository.owner_id = account.id
          where
          repository.name = {repoName} and
          account.username = {owner}
        """)
          .as(SqlParser.int("repository.id").single)

      }
    }(ec)

  def createCollaborator(repositoryId: Int, collaboratorId: Int, role: Int): Future[Option[Long]] = Future {
    db.withConnection { implicit connection =>
      SQL("""
          insert into collaborator (account_id, repository_id, role) values ({accountId}, {repositoryId}, {role})
        """)
        .on("accountId" -> collaboratorId, "repositoryId" -> repositoryId, "role" -> role)
        .executeInsert()
    }
  }

  def removeCollaborator(repositoryId: Long, collaboratorId: Long): Future[Int] = Future {
    db.withConnection { implicit connection =>
      SQL("""
          delete from collaborator where account_id={accountId} and repository_id={repositoryId}
        """).on("accountId" -> collaboratorId, "repositoryId" -> repositoryId).executeUpdate()
    }
  }

  /**
   * Insert a new repository
   *
   */
  def insertRepository(ownerId: Int, repository: RepositoryData): Future[Option[Long]] =
    Future {
      db.withConnection { implicit connection =>
        SQL("""
          insert into repository (name, owner_id, description, default_branch) values
            ({name},{owner_id},{description},{defaultBranch})
        """)
          .on(
            "name"          -> repository.name,
            "owner_id"      -> ownerId,
            "description"   -> repository.description.getOrElse(""),
            "defaultBranch" -> RzRepository.defaultBranchName
          )
          .executeInsert()
      }
    }(ec)

  def isAccountCollaborator(repository: Option[RzRepository], accountId: Long): Future[Option[Int]] =
    repository match {
      case Some(repo) =>
        Future {
          db.withConnection { implicit connection =>
            SQL("""
              select role from collaborator
              where collaborator.account_id = {collaboratorId} and repository_id = {repositoryId}
              """)
              .on("collaboratorId" -> accountId, "repositoryId" -> repo.id)
              .as(SqlParser.int("role").singleOpt)
          }
        }(ec)
      case None => Future(None)
    }
  def getCollaborators(repository: RzRepository): Future[List[Collaborator]] =
    Future {
      db.withConnection { implicit connection =>
        SQL("""
          select * from collaborator
          join account
          on account.id = collaborator.account_id
          where repository_id = {repositoryId}
          order by collaborator.id asc
        """)
          .on("repositoryId" -> repository.id)
          .as(collaboratorParser.*)
      }
    }(ec)

  def listRepositories(accountId: Long): Future[List[RzRepository]] =
    Future {
      db.withConnection { implicit connection =>
        SQL("""
          select repository.id, repository.name, repository.default_branch, repository.main_file,
          account.id, account.username, account.has_picture, account.email
          from repository
          join account on repository.owner_id = account.id
          left join collaborator on repository.id = collaborator.repository_id
          where repository.owner_id = {accountId}
          or collaborator.account_id = {accountId}
          order by repository.id desc
        """)
          .on("accountId" -> accountId)
          .as(simpleGitRepositoryParser.*)
      }
    }(ec)
}
