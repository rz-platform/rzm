package models

import java.io.{ File, InputStream }
import java.util.Date

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
}
