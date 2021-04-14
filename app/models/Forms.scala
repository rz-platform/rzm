package models

import org.apache.commons.io.FileUtils
import org.eclipse.jgit.revwalk.RevCommit
import play.api.mvc.MultipartFormData.FilePart

import java.io.File
import java.nio.file.Path

// Forms related to auth

case class AccountRegistrationData(userName: String, fullName: Option[String], password: String, email: String)

case class AccountData(userName: String, fullName: Option[String], email: String)

case class RepositoryData(name: String, description: Option[String])

case class PasswordData(oldPassword: String, newPassword: String)

case class AccountLoginData(userName: String, password: String)

// Forms related to project control

case class CommitFile(id: String, name: String, content: Array[Byte])

object CommitFile {
  def fromFile(f: File, path: Path): CommitFile = {
    val filename = f.getName
    val filePath = RzPathUrl.make(FilePath.relativize(path, f.getAbsolutePath), f.getName, isFolder = false).uri
    val content  = FileUtils.readFileToByteArray(f)
    CommitFile(filename, name = filePath, content)
  }

  def fromFilePart(f: FilePart[File], path: String): CommitFile = {
    val filename = f.filename
    val filePath = RzPathUrl.make(path, filename, isFolder = false).uri
    val content  = FileUtils.readFileToByteArray(f.ref)
    CommitFile(filename, name = filePath, content)
  }
}

case class EditedItem(content: String, rev: String, path: String, name: String)

case class UploadFileForm(path: String)

case class RepositoryGitData(files: List[FileInfo], lastCommit: Option[RevCommit])

case class NewItem(name: String, rev: String, path: String, isFolder: Boolean)

case class SshKeyData(publicKey: String)

case class SshRemoveData(id: String)

case class NewCollaboratorData(emailOrLogin: String, role: String)

case class RemoveCollaboratorData(id: String)

// Templates

case class TemplateData(name: String)
