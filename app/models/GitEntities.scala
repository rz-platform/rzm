package models

import java.io.{ File, InputStream }
import java.time.{ LocalDateTime, ZoneId }

import anorm._
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit

case class Repository(
  id: Long,
  owner: SimpleAccount,
  name: String,
  defaultBranch: String
)

object Repository {
  implicit def toParameters: ToParameterList[Repository] = Macro.toParameters[Repository]
  val defaultBranchName                                  = "master"
}

case class RepositoryData(name: String, description: Option[String])

case class CommitFile(id: String, name: String, file: File)

case class EditedItem(content: String, rev: String, path: String, fileName: String)

case class UploadFileForm(path: String)

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

case class RepositoryGitData(files: List[FileInfo], lastCommit: Option[RevCommit])

case class NewItem(name: String, rev: String, path: String, isFolder: Boolean)

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

case class Blob(
  content: ContentInfo,
  latestCommit: CommitInfo,
  isLfsFile: Boolean
)

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
