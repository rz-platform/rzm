package models

import infrastructure.RzDateTime
import org.eclipse.jgit.lib.ObjectId

import java.io.InputStream
import java.time.{ LocalDateTime, ZoneId }

case class RzRepository(
  id: String,
  owner: Account,
  name: String,
  createdAt: Long,
  updatedAt: Option[Long]
) extends PersistentEntityMap {
  val prefix: String = RedisKeyPrefix.rzRepoPrefix

  def http(request: RepositoryRequestHeader): String = s"https://${request.host}/${owner.username}/$name.git"

  def ssh(request: RepositoryRequestHeader): String = s"git@${request.host}:${owner.username}/$name.git"

  val toMap: Map[String, String] = {
    // take advantage of the iterable nature of Option
    val updatedAt = if (this.updatedAt.nonEmpty) Some("updatedAt" -> this.updatedAt.get.toString) else None
    (Seq("createdAt" -> createdAt.toString) ++ updatedAt).toMap
  }

  def this(owner: Account, name: String) = this(PersistentEntity.id, owner, name, RzDateTime.now, None)
}

object RzRepository {
  def key(owner: Account, id: String): String = PersistentEntity.key(RedisKeyPrefix.rzRepoPrefix, owner.id, id)

  val defaultBranch = "master"

  def make(id: String, name: String, owner: Account, data: Map[String, String]): Either[RzError, RzRepository] =
    (for {
      createdAt <- data.get("createdAt")
    } yield RzRepository(
      id,
      owner,
      name,
      RzDateTime.parseTimestamp(createdAt),
      RzDateTime.parseTimestamp(data.get("updatedAt"))
    )) match {
      case Some(a) => Right(a)
      case None    => Left(ParsingError)
    }
}

case class RzRepositoryConfig(
  repo: RzRepository,
  entrypoint: Option[String],
  compiler: RzCompiler,
  bibliography: RzBib
) extends PersistentEntityMap {
  val id: String     = repo.id
  val prefix: String = RedisKeyPrefix.rzRepoConfPrefix

  val toMap: Map[String, String] = {
    // take advantage of the iterable nature of Option
    val entrypoint = if (this.entrypoint.nonEmpty) Some("entrypoint" -> this.entrypoint.get) else None
    (Seq("compiler" -> compiler.id, "bibliography" -> bibliography.id) ++ entrypoint).toMap
  }
}

object RzRepositoryConfig {
  def makeDefault(
    repository: RzRepository,
    entrypoint: Option[String],
    compiler: Option[RzCompiler],
    bibliography: Option[RzBib]
  ): RzRepositoryConfig =
    RzRepositoryConfig(repository, entrypoint, compiler.getOrElse(PdfLatex), bibliography.getOrElse(BibLatex))

  def make(repository: RzRepository, data: Map[String, String]): Either[RzError, RzRepositoryConfig] =
    (for {
      compilerId     <- data.get("compiler")
      bibliographyId <- data.get("bibliography")
      compiler       <- RzCompiler.make(compilerId)
      bib            <- RzBib.make(bibliographyId)
    } yield RzRepositoryConfig(
      repository,
      data.get("entrypoint"),
      compiler,
      bib
    )) match {
      case Some(a) => Right(a)
      case None    => Left(ParsingError)
    }
}

object LastOpenedFile {
  def asEntity(account: Account, repo: RzRepository, fileName: String) = {
    val id = PersistentEntity.key(account.id, repo.owner.id, repo.id)
    PersistentEntityString(RedisKeyPrefix.lastOpenedFilePrefix, id, fileName)
  }
  def asKey(account: Account, repo: RzRepository) = {
    PersistentEntity.key(account.id, repo.owner.id, repo.id)
  }

}

object RepositoryCollaborators {
  def asKey(repo: RzRepository) = PersistentEntity.key(RedisKeyPrefix.rzRepoCollaboratorsPrefix, repo.id)
}

object RepositoryName {
  def asEntity(repo: RzRepository) = PersistentEntityString(RedisKeyPrefix.rzRepoNamePrefix, repo.name, repo.id)
  def asKey(name: String): String = PersistentEntity.key(RedisKeyPrefix.rzRepoNamePrefix, name)
}

/**
 * The file data for the file list of the repository viewer.
 *
 */
case class FileInfo(
  id: ObjectId,
  isDirectory: Boolean,
  name: String,
  path: String,
  message: String,
  commitId: String,
  author: String,
  mailAddress: String
)

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
  lazy val lineSeparator: String = if (content.exists(_.indexOf("\r\n") >= 0)) "CRLF" else "LF"
}

sealed trait RepositoryTreeContent

case class Blob(
  content: ContentInfo,
  latestCommit: CommitInfo
) extends RepositoryTreeContent

case object EmptyBlob extends RepositoryTreeContent

case object EmptyRepository extends RepositoryTreeContent

case class RawFile(inputStream: InputStream, contentLength: Integer, contentType: String)

/**
 * The commit data.
 *
 */
case class CommitInfo(
  id: String,
  shortMessage: String,
  fullMessage: String,
  parents: List[String],
  authorName: String,
  authorEmailAddress: String,
  commitTime: LocalDateTime,
  committerName: String,
  committerEmailAddress: String
) {
  def this(rev: org.eclipse.jgit.revwalk.RevCommit) =
    this(
      rev.getName,
      rev.getShortMessage,
      rev.getFullMessage,
      rev.getParents.map(_.name).toList,
      rev.getAuthorIdent.getName,
      rev.getAuthorIdent.getEmailAddress,
      rev.getCommitterIdent.getWhen.toInstant.atZone(ZoneId.systemDefault()).toLocalDateTime,
      rev.getCommitterIdent.getName,
      rev.getCommitterIdent.getEmailAddress
    )
}
