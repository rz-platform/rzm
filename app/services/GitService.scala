package services

import infrastructure.{FileSystem, GitStorage}
import models._
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipArchiveOutputStream}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.{DirCache, DirCacheBuilder}
import org.eclipse.jgit.lib.{Repository => _, _}
import org.eclipse.jgit.treewalk.TreeWalk

import java.io.File
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using

class GitService @Inject() (storage: GitStorage)(
  implicit ec: ExecutionContext
) {
  def initRepo(repo: RzRepository): Future[_] = Future {
    Using(new RepositoryBuilder().setGitDir(storage.repositoryDir(repo)).setBare().build) { repository =>
      repository.create(true)
      storage.setReceivePack(repository)
    }
  }

  def fileList(repo: RzRepository, revstr: String = "", path: String = "."): Future[Option[RepositoryGitData]] =
    Future {
      Using.resource(Git.open(storage.repositoryDir(repo))) { git =>
        if (storage.isEmpty(git)) {
          None
        } else {
          storage.getDefaultBranch(git, revstr).map {
            case (objectId, revision) =>
              storage.defining(storage.getRevCommitFromId(git, objectId)) { revCommit =>
                val lastModifiedCommit =
                  if (path == ".") revCommit else storage.getLastModifiedCommit(git, revCommit, path)
                // get files
                val files = storage.getFileList(git, revision, path)

                RepositoryGitData(files, Some(lastModifiedCommit))
              }
          }
        }
      }
    }

  def commitFiles(repo: RzRepository, branch: String, message: String, account: Account)(
    f: (Git, ObjectId, DirCacheBuilder, ObjectInserter) => Unit
  ): Future[ObjectId] = Future {
    storage.lock(s"${repo.owner.userName}/${repo.name}") {
      Using.resource(Git.open(storage.repositoryDir(repo))) { git =>
        val builder  = DirCache.newInCore.builder()
        val inserter = git.getRepository.newObjectInserter()
        val headName = s"refs/heads/$branch"
        val headTip  = git.getRepository.resolve(headName)

        f(git, headTip, builder, inserter)

        val commitId = storage.createNewCommit(
          git,
          inserter,
          headTip,
          builder.getDirCache.writeTree(inserter),
          headName,
          account.userName,
          account.email,
          message
        )

        inserter.flush()
        inserter.close()

        commitId
      }
    }
  }

  def fileTree(repo: RzRepository, revstr: String): Future[FileTree] = Future {
    val fileTree = new FileTree(FileNode(".", "."))

    Using.resource(Git.open(storage.repositoryDir(repo))) { git =>
      storage.getDefaultBranch(git, revstr).map {
        case (objectId, _) =>
          storage.defining(storage.getRevCommitFromId(git, objectId)) { revCommit =>
            val treeWalk = new TreeWalk(git.getRepository)
            treeWalk.addTree(revCommit.getTree)
            treeWalk.setRecursive(false)
            while (treeWalk.next()) {
              if (treeWalk.isSubtree) {
                treeWalk.enterSubtree()
              } else {
                fileTree.addElement(treeWalk.getPathString)
              }
            }
            fileTree
          }
      }.getOrElse(fileTree)
    }
  }

  def commitFiles(
    repo: RzRepository,
    files: Seq[CommitFile],
    account: Account,
    rev: String,
    message: String
  ): Future[ObjectId] =
    commitFiles(repo, rev, message, account) {
      case (git, headTip, builder, inserter) =>
        storage.processTree(git, headTip) { (path, tree) =>
          if (!files.exists(_.name.contains(path))) {
            builder.add(
              storage.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId)
            )
          }
        }

        files.foreach { file =>
          val bytes: Array[Byte] = file.content
          builder.add(
            storage.createDirCacheEntry(file.name, FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, bytes))
          )
          builder.finish()
        }
    }

  def blobFile(repo: RzRepository, path: String, rev: String): Future[Option[Blob]] = Future {
    Using.resource(Git.open(storage.repositoryDir(repo))) { git =>
      val revCommit = storage.getRevCommitFromId(git, git.getRepository.resolve(rev))
      storage.getPathObjectId(git, path, revCommit).map { objectId =>
        val content = storage.getContentInfo(git, path, objectId)
        val commitInfo = new CommitInfo(storage.getLastModifiedCommit(git, revCommit, path))
        Blob(content, commitInfo)
      }
    }
  }


  def commitFile(
    account: Account,
    repo: RzRepository,
    oldPath: RzPathUrl,
    newPath: RzPathUrl,
    content: Array[Byte],
    message: String
  ): Future[ObjectId] =
    commitFiles(
      repo,
      oldPath.pathWithoutFilename,
      message,
      account
    ) {
      case (git_, headTip, builder, inserter) =>
        val permission = storage
          .processTree(git_, headTip) { (path, tree) =>
            // Add all entries except the editing file
            if (!newPath.uri.contains(path) && !oldPath.uri.contains(path)) {
              builder.add(storage.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId))
            }
            // Retrieve permission if file exists to keep it
            oldPath.uri.collect { case x if x.toString == path => tree.getEntryFileMode.getBits }
          }
          .flatten
          .headOption

        builder.add(
          storage.createDirCacheEntry(
            newPath.uri,
            permission.map(bits => FileMode.fromBits(bits)).getOrElse(FileMode.REGULAR_FILE),
            inserter.insert(Constants.OBJ_BLOB, content)
          )
        )
        builder.finish()
    }

  def commitNewFile(
    account: Account,
    repo: RzRepository,
    fName: RzPathUrl,
    newItem: NewItem,
    message: String
  ): Future[_] = commitFiles(repo, newItem.rev, message, account) {
    case (git_, headTip, builder, inserter) =>
      storage.processTree(git_, headTip) { (path, tree) =>
        if (!fName.uri.contains(path)) {
          builder.add(
            storage.createDirCacheEntry(path, tree.getEntryFileMode, tree.getEntryObjectId)
          )
        }
      }
      val emptyArray = Array.empty[Byte]
      builder.add(
        storage.createDirCacheEntry(
          fName.uri,
          FileMode.REGULAR_FILE,
          inserter.insert(Constants.OBJ_BLOB, emptyArray)
        )
      )
      builder.finish()
  }

  def rawFile(repo: RzRepository, rev: String, path: String): Future[Option[RawFile]] = Future {
    Using.resource(Git.open(storage.repositoryDir(repo))) { git =>
      val revCommit = storage.getRevCommitFromId(git, git.getRepository.resolve(rev))
      storage.getPathObjectId(git, path, revCommit).flatMap { objectId =>
        storage.getObjectLoaderFromId(git, objectId) { loader =>
          RawFile(loader.openStream(), loader.getSize.toInt, FileSystem.getSafeMimeType(path))
        }
      }
    }
  }

  def createArchive(repo: RzRepository, path: String, revision: String): Future[File] = Future {
    val tempFile = File.createTempFile(repo.name + "-" + revision, ".archive.zip")
    Using.resource(new ZipArchiveOutputStream(tempFile)) { zip =>
      storage.archiveRepo(repo, path, revision, zip) { (path, size, date, mode) =>
        val entry = new ZipArchiveEntry(path)
        entry.setSize(size)
        entry.setUnixMode(mode)
        entry.setTime(date.getTime)
        entry
      }
    }
    tempFile
  }

  def commitsLog(
    repo: RzRepository,
    revision: String,
    page: Int,
    limit: Int,
    path: String = ""
  ): Future[Either[String, (List[CommitInfo], Boolean)]] = Future {
    Using.resource(Git.open(storage.repositoryDir(repo)))(git => storage.getCommitLog(git, revision, page, limit, path))
  }
}
