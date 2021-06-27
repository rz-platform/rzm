package documents.models

import org.apache.commons.io.FileUtils
import play.api.mvc.MultipartFormData.FilePart

import java.io.File
import java.nio.file.Path

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
