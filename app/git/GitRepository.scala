package git

import models._
import org.apache.commons.compress.archivers.{ArchiveEntry, ArchiveOutputStream}
import java.io.{File, FileInputStream, FileOutputStream, InputStream}

import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipArchiveOutputStream}
import org.apache.commons.compress.utils.{IOUtils => CompressIOUtils}
import org.apache.commons.io.IOUtils
import org.apache.commons.io.input.BOMInputStream
import org.apache.tika.Tika
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.{DirCache, DirCacheBuilder, DirCacheEntry}
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.lib.{Repository => _, _}
import org.eclipse.jgit.revwalk.{RevCommit, RevTag, RevTree, RevWalk}
import org.eclipse.jgit.transport.{ReceiveCommand, ReceivePack}
import org.eclipse.jgit.treewalk.TreeWalk.OperationType
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.treewalk.{CanonicalTreeParser, TreeWalk, WorkingTreeOptions}
import org.eclipse.jgit.util.io.EolStreamTypeUtil
import org.mozilla.universalchardet.UniversalDetector

import scala.util.Using.Releasable
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.util.Using

// Package contains parts of code inherited from GitBucket project

class GitRepository(val owner: Account, val repositoryName: String, val gitHome: String) {
  def repositoryDir: File = new File(s"${gitHome}/${owner.userName}/${repositoryName}.git")

  implicit val objectDatabaseReleasable = new Releasable[ObjectDatabase] {
    override def release(resource: ObjectDatabase): Unit = resource.close()
  }

  def fileList(repository: Repository, revstr: String = "", path: String = "."): Option[RepositoryGitData] = {
    Using.resource(Git.open(repositoryDir)) { git =>
      if (isEmpty(git)) {
        None
      } else {
        getDefaultBranch(git, repository, revstr).map {
          case (objectId, revision) =>
            defining(getRevCommitFromId(git, objectId)) { revCommit =>
              val lastModifiedCommit = if (path == ".") revCommit else getLastModifiedCommit(git, revCommit, path)
              // get files
              val files = getFileList(git, revision, path, None)
              val parentPath = if (path == ".") Nil else path.split("/").toList

              // process README.md or README.markdown
              val readme = files
                .find { file =>
                  !file.isDirectory && readmeFiles.contains(file.name.toLowerCase)
                }
                .map { file =>
                  val path = (file.name :: parentPath.reverse).reverse
                  path -> convertFromByteArray(
                    getContentFromId(Git.open(repositoryDir), file.id, true).get
                  )
                }
              RepositoryGitData(files, Some(lastModifiedCommit))
            }
        }
      }
    }
  }

  protected def getPathObjectId(git: Git, path: String, revCommit: RevCommit): Option[ObjectId] = {
    @scala.annotation.tailrec
    def _getPathObjectId(path: String, walk: TreeWalk): Option[ObjectId] = walk.next match {
      case true if (walk.getPathString == path) => Some(walk.getObjectId(0))
      case true                                 => _getPathObjectId(path, walk)
      case false                                => None
    }

    Using.resource(new TreeWalk(git.getRepository)) { treeWalk =>
      treeWalk.addTree(revCommit.getTree)
      treeWalk.setRecursive(true)
      _getPathObjectId(path, treeWalk)
    }
  }

  def getContentInfo(git: Git, path: String, objectId: ObjectId): ContentInfo = {
    // Viewer
    Using.resource(git.getRepository.getObjectDatabase) { db =>
      val loader = db.open(objectId)
      val isLfs = isLfsPointer(loader)
      val large = isLarge(loader.getSize)
      val viewer = if (isImage(path)) "image" else if (large) "large" else "other"
      val bytes = if (viewer == "other") getContentFromId(git, objectId, false) else None
      val size = Some(getContentSize(loader))

      if (viewer == "other") {
        if (!isLfs && bytes.isDefined && isText(bytes.get)) {
          // text
          ContentInfo(
            "text",
            size,
            Some(convertFromByteArray(bytes.get)),
            Some(detectEncoding(bytes.get))
          )
        } else {
          // binary
          ContentInfo("binary", size, None, None)
        }
      } else {
        // image or large
        ContentInfo(viewer, size, None, None)
      }
    }
  }

  def isText(content: Array[Byte]): Boolean = !content.contains(0)

  def getMimeType(name: String): String =
    defining(new Tika()) { tika =>
      tika.detect(name) match {
        case null     => "application/octet-stream"
        case mimeType => mimeType
      }
    }

  def getLfsObjects(text: String): Map[String, String] = {
    if (text.startsWith("version https://git-lfs.github.com/spec/v1")) {
      // LFS objects
      text
        .split("\n")
        .map { line =>
          val dim = line.split(" ")
          dim(0) -> dim(1)
        }
        .toMap
    } else {
      Map.empty
    }
  }

  def isImage(name: String): Boolean = getMimeType(name).startsWith("image/")

  def isLfsPointer(loader: ObjectLoader): Boolean = {
    !loader.isLarge && new String(loader.getBytes(), "UTF-8").startsWith("version https://git-lfs.github.com/spec/v1")
  }

  def getContentSize(loader: ObjectLoader): Long = {
    if (loader.isLarge) {
      loader.getSize
    } else {
      val bytes = loader.getCachedBytes
      val text = new String(bytes, "UTF-8")

      val attr = getLfsObjects(text)
      attr.get("size") match {
        case Some(size) => size.toLong
        case None       => loader.getSize
      }
    }
  }

  def blobFile(path: String, rev: String): Option[Blob] = {
    Using.resource(Git.open(repositoryDir)) { git =>
      val revCommit = getRevCommitFromId(git, git.getRepository.resolve(rev))
      getPathObjectId(git, path, revCommit).map { objectId =>
        {
          val content = getContentInfo(git, path, objectId)
          val commitInfo = new CommitInfo(getLastModifiedCommit(git, revCommit, path))
          val isLfs = isLfsFile(git, objectId)
          Blob(content, commitInfo, isLfs)
        }
      }
    }
  }

  private def isLfsFile(git: Git, objectId: ObjectId): Boolean = {
    getObjectLoaderFromId(git, objectId)(isLfsPointer).getOrElse(false)
  }

  /**
   * Get objectLoader of the given object id from the Git repository.
   *
   * @param git the Git object
   * @param id the object id
   * @param f the function process ObjectLoader
   * @return None if object does not exist
   */
  def getObjectLoaderFromId[A](git: Git, id: ObjectId)(f: ObjectLoader => A): Option[A] =
    try {
      Using.resource(git.getRepository.getObjectDatabase) { db =>
        Some(f(db.open(id)))
      }
    } catch {
      case e: MissingObjectException => None
    }

  def detectEncoding(content: Array[Byte]): String =
    defining(new UniversalDetector(null)) { detector =>
      detector.handleData(content, 0, content.length)
      detector.dataEnd()
      detector.getDetectedCharset match {
        case null => "UTF-8"
        case e    => e
      }
    }

  def isLarge(size: Long): Boolean = (size > 1024 * 1000)

  def convertFromByteArray(content: Array[Byte]): String =
    IOUtils.toString(new BOMInputStream(new java.io.ByteArrayInputStream(content)), detectEncoding(content))

  /**
   * Get object content of the given object id as byte array from the Git repository.
   *
   * @param git the Git object
   * @param id the object id
   * @param fetchLargeFile if false then returns None for the large file
   * @return the byte array of content or None if object does not exist
   */
  def getContentFromId(git: Git, id: ObjectId, fetchLargeFile: Boolean): Option[Array[Byte]] =
    try {
      Using.resource(git.getRepository.getObjectDatabase) { db =>
        val loader = db.open(id)
        if (loader.isLarge || (!fetchLargeFile && isLarge(loader.getSize))) {
          None
        } else {
          Some(loader.getBytes)
        }
      }
    } catch {
      case e: MissingObjectException => None
    }

  val readmeFiles = Seq("readme.txt", "readme")

  /**
   * Returns the file list of the specified path.
   *
   * @param git      the Git object
   * @param revision the branch name or commit id
   * @param path     the directory path (optional)
   * @param baseUrl  the base url of GitBucket instance. This parameter is used to generate links of submodules (optional)
   * @return HTML of the file list
   */
  def getFileList(git: Git, revision: String, path: String = ".", baseUrl: Option[String] = None): List[FileInfo] = {
    Using.resource(new RevWalk(git.getRepository)) { revWalk =>
      val objectId = git.getRepository.resolve(revision)
      if (objectId == null) return Nil
      val revCommit = revWalk.parseCommit(objectId)

      def useTreeWalk(rev: RevCommit)(f: TreeWalk => Any): Unit =
        if (path == ".") {
          val treeWalk = new TreeWalk(git.getRepository)
          treeWalk.addTree(rev.getTree)
          Using.resource(treeWalk)(f)
        } else {
          val treeWalk = TreeWalk.forPath(git.getRepository, path, rev.getTree)
          if (treeWalk != null) {
            treeWalk.enterSubtree
            Using.resource(treeWalk)(f)
          }
        }

      @tailrec
      def simplifyPath(
        tuple: (ObjectId, FileMode, String, String, Option[String], RevCommit)
      ): (ObjectId, FileMode, String, String, Option[String], RevCommit) = tuple match {
        case (oid, FileMode.TREE, name, path, _, commit) =>
          Using.resource(new TreeWalk(git.getRepository)) { walk =>
            walk.addTree(oid)
            // single tree child, or None
            if (walk.next() && walk.getFileMode(0) == FileMode.TREE) {
              Some(
                (
                  walk.getObjectId(0),
                  walk.getFileMode(0),
                  name + "/" + walk.getNameString,
                  path + "/" + walk.getNameString,
                  None,
                  commit
                )
              ).filterNot(_ => walk.next())
            } else {
              None
            }
          } match {
            case Some(child) => simplifyPath(child)
            case _           => tuple
          }
        case _ => tuple
      }

      def tupleAdd(tuple: (ObjectId, FileMode, String, String, Option[String]), rev: RevCommit) = tuple match {
        case (oid, fmode, name, path, opt) => (oid, fmode, name, path, opt, rev)
      }

      @tailrec
      def findLastCommits(
        result: List[(ObjectId, FileMode, String, String, Option[String], RevCommit)],
        restList: List[((ObjectId, FileMode, String, String, Option[String]), Map[RevCommit, RevCommit])],
        revIterator: java.util.Iterator[RevCommit]
      ): List[(ObjectId, FileMode, String, String, Option[String], RevCommit)] = {
        if (restList.isEmpty) {
          result
        } else if (!revIterator.hasNext) { // maybe, revCommit has only 1 log. other case, restList be empty
          result ++ restList.map { case (tuple, map) => tupleAdd(tuple, map.values.headOption.getOrElse(revCommit)) }
        } else {
          val newCommit = revIterator.next
          val (thisTimeChecks, skips) = restList.partition {
            case (tuple, parentsMap) => parentsMap.contains(newCommit)
          }
          if (thisTimeChecks.isEmpty) {
            findLastCommits(result, restList, revIterator)
          } else {
            var nextRest = skips
            var nextResult = result
            // Map[(name, oid), (tuple, parentsMap)]
            val rest = scala.collection.mutable.Map(thisTimeChecks.map { t =>
              (t._1._3 -> t._1._1) -> t
            }: _*)
            lazy val newParentsMap = newCommit.getParents.map(_ -> newCommit).toMap
            useTreeWalk(newCommit) { walk =>
              while (walk.next) {
                rest.remove(walk.getNameString -> walk.getObjectId(0)).foreach {
                  case (tuple, _) =>
                    if (newParentsMap.isEmpty) {
                      nextResult +:= tupleAdd(tuple, newCommit)
                    } else {
                      nextRest +:= tuple -> newParentsMap
                    }
                }
              }
            }
            rest.values.foreach {
              case (tuple, parentsMap) =>
                val restParentsMap = parentsMap - newCommit
                if (restParentsMap.isEmpty) {
                  nextResult +:= tupleAdd(tuple, parentsMap(newCommit))
                } else {
                  nextRest +:= tuple -> restParentsMap
                }
            }
            findLastCommits(nextResult, nextRest, revIterator)
          }
        }
      }

      var fileList: List[(ObjectId, FileMode, String, String, Option[String])] = Nil
      useTreeWalk(revCommit) { treeWalk =>
        while (treeWalk.next()) {
          val linkUrl = None
          fileList +:= (treeWalk.getObjectId(0), treeWalk.getFileMode(0), treeWalk.getNameString, treeWalk.getPathString, linkUrl)
        }
      }
      revWalk.markStart(revCommit)
      val it = revWalk.iterator
      val lastCommit = it.next
      val nextParentsMap = Option(lastCommit).map(_.getParents.map(_ -> lastCommit).toMap).getOrElse(Map())
      findLastCommits(List.empty, fileList.map(a => a -> nextParentsMap), it)
        .map(simplifyPath)
        .map {
          case (objectId, fileMode, name, path, linkUrl, commit) =>
            FileInfo(
              objectId,
              fileMode == FileMode.TREE || fileMode == FileMode.GITLINK,
              name,
              path,
              getSummaryMessage(commit.getFullMessage, commit.getShortMessage),
              commit.getName,
              commit.getAuthorIdent.getWhen,
              commit.getAuthorIdent.getName,
              commit.getAuthorIdent.getEmailAddress,
              linkUrl
            )
        }
        .sortWith { (file1, file2) =>
          (file1.isDirectory, file2.isDirectory) match {
            case (true, false) => true
            case (false, true) => false
            case _             => file1.name.compareTo(file2.name) < 0
          }
        }
    }
  }

  /**
   * Returns the first line of the commit message.
   */
  private def getSummaryMessage(fullMessage: String, shortMessage: String): String = {
    defining(fullMessage.trim.indexOf('\n')) { i =>
      defining(if (i >= 0) fullMessage.trim.substring(0, i).trim else fullMessage) { firstLine =>
        if (firstLine.length > shortMessage.length) shortMessage else firstLine
      }
    }
  }

  private def getDefaultBranch(git: Git, repository: Repository, revstr: String): Option[(ObjectId, String)] = {
    Using.resource(Git.open(repositoryDir)) { git =>
      val branchList = git.branchList.call.asScala.map { ref =>
        ref.getName.stripPrefix("refs/heads/")
      }.toList

      Seq(
        Some(if (revstr.isEmpty) repository.defaultBranch else revstr),
        branchList.headOption
      ).flatMap {
          case Some(rev) => Some((git.getRepository.resolve(rev), rev))
          case None      => None
        }
        .find(_._1 != null)
    }
  }

  def commitFiles(branch: String, path: String, message: String, loginAccount: Account)(
    f: (Git, ObjectId, DirCacheBuilder, ObjectInserter) => Unit
  ) = {
    LockUtil.lock(s"${owner.userName}/${repositoryName}") {
      Using.resource(Git.open(repositoryDir)) { git =>
        val builder = DirCache.newInCore.builder()
        val inserter = git.getRepository.newObjectInserter()
        val headName = s"refs/heads/${branch}"
        val headTip = git.getRepository.resolve(headName)

        f(git, headTip, builder, inserter)

        val commitId = createNewCommit(
          git,
          inserter,
          headTip,
          builder.getDirCache.writeTree(inserter),
          headName,
          loginAccount.userName, // TODO: fullName
          loginAccount.mailAddress,
          message
        )

        inserter.flush()
        inserter.close()

        val receivePack = new ReceivePack(git.getRepository)
        val receiveCommand = new ReceiveCommand(headTip, commitId, headName)
        commitId
      }
    }
  }

  def processTree[T](git: Git, id: ObjectId)(f: (String, CanonicalTreeParser) => T): Seq[T] = {
    Using.resource(new RevWalk(git.getRepository)) { revWalk =>
      Using.resource(new TreeWalk(git.getRepository)) { treeWalk =>
        val index = treeWalk.addTree(revWalk.parseTree(id))
        treeWalk.setRecursive(true)
        val result = new collection.mutable.ListBuffer[T]()
        while (treeWalk.next) {
          result += f(treeWalk.getPathString, treeWalk.getTree(index, classOf[CanonicalTreeParser]))
        }
        result.toSeq
      }
    }
  }

  def create(): Unit = {
    initRepo()
    createFile("README.md", "Initial commit", owner)
  }

  def createFile(fileName: String, commitName: String, account: Account): Unit = {
    Using.resource(Git.open(repositoryDir)) { git =>
      val builder = DirCache.newInCore.builder()
      val inserter = git.getRepository.newObjectInserter()
      val headId = git.getRepository.resolve(Constants.HEAD + "^{commit}")

      val content = "content"

      builder.add(
        createDirCacheEntry(
          fileName,
          FileMode.REGULAR_FILE,
          inserter.insert(Constants.OBJ_BLOB, content.getBytes("UTF-8"))
        )
      )
      builder.finish()

      createNewCommit(
        git,
        inserter,
        headId,
        builder.getDirCache.writeTree(inserter),
        Constants.HEAD,
        account.userName, // TODO: fullName
        account.mailAddress,
        commitName
      )
    }
  }

  private def initRepo(): Unit = {
    Using(new RepositoryBuilder().setGitDir(repositoryDir).setBare.build) { repository =>
      repository.create(true)
      setReceivePack(repository)
    }
  }

  private def defining[A, B](value: A)(f: A => B): B = f(value)

  private def setReceivePack(repository: org.eclipse.jgit.lib.Repository): Unit =
    defining(repository.getConfig) { config =>
      config.setBoolean("http", null, "receivepack", true)
      config.save
    }

  def createDirCacheEntry(path: String, mode: FileMode, objectId: ObjectId): DirCacheEntry = {
    val entry = new DirCacheEntry(path)
    entry.setFileMode(mode)
    entry.setObjectId(objectId)
    entry
  }

  def isEmpty(git: Git): Boolean = git.getRepository.resolve(Constants.HEAD) == null

  def createNewCommit(
    git: Git,
    inserter: ObjectInserter,
    headId: AnyObjectId,
    treeId: AnyObjectId,
    ref: String,
    fullName: String,
    mailAddress: String,
    message: String
  ): ObjectId = {
    val newCommit = new CommitBuilder()
    newCommit.setCommitter(new PersonIdent(fullName, mailAddress))
    newCommit.setAuthor(new PersonIdent(fullName, mailAddress))
    newCommit.setMessage(message)
    if (headId != null) {
      newCommit.setParentIds(List(headId).asJava)
    }
    newCommit.setTreeId(treeId)

    val newHeadId = inserter.insert(newCommit)
    inserter.flush()
    inserter.close()

    val refUpdate = git.getRepository.updateRef(ref)
    refUpdate.setNewObjectId(newHeadId)
    refUpdate.update()

    newHeadId
  }

  /**
   * Returns the last modified commit of specified path
   *
   * @param git         the Git object
   * @param startCommit the search base commit id
   * @param path        the path of target file or directory
   * @return the last modified commit of specified path
   */
  def getLastModifiedCommit(git: Git, startCommit: RevCommit, path: String): RevCommit = {
    git.log.add(startCommit).addPath(path).setMaxCount(1).call.iterator.next
  }

  /**
   * Returns RevCommit from the commit or tag id.
   *
   * @param git      the Git object
   * @param objectId the ObjectId of the commit or tag
   * @return the RevCommit for the specified commit or tag
   */
  def getRevCommitFromId(git: Git, objectId: ObjectId): RevCommit = {
    val revWalk = new RevWalk(git.getRepository)
    val revCommit = revWalk.parseAny(objectId) match {
      case r: RevTag => revWalk.parseCommit(r.getObject)
      case _         => revWalk.parseCommit(objectId)
    }
    revWalk.dispose
    revCommit
  }

  private def openFile[T](git: Git, treeWalk: TreeWalk)(
    f: InputStream => T
  ): T = {
    val attrs = treeWalk.getAttributes
    val loader = git.getRepository.open(treeWalk.getObjectId(0))
    Using.resource(loader.openStream()) { in =>
      f(in)
    }
  }

  def openFile[T](git: Git, tree: RevTree, path: String)(
    f: InputStream => T
  ): T = {
    Using.resource(TreeWalk.forPath(git.getRepository, path, tree)) { treeWalk =>
      openFile(git, treeWalk)(f)
    }
  }

  private def archiveRepo(path: String, revision: String, archive: ArchiveOutputStream)(
    entryCreator: (String, Long, java.util.Date, Int) => ArchiveEntry
  ): Unit = {
    Using.resource(Git.open(repositoryDir)) { git =>
      val oid = git.getRepository.resolve(revision)
      val commit = getRevCommitFromId(git, oid)
      val date = commit.getCommitterIdent.getWhen
      val sha1 = oid.getName()
      val repositorySuffix = (if (sha1.startsWith(revision)) sha1 else revision).replace('/', '-')
      val pathSuffix = if (path.isEmpty) "" else s"-${path.replace('/', '-')}"
      val baseName = repositoryName + "-" + repositorySuffix + pathSuffix

      Using.resource(new TreeWalk(git.getRepository)) { treeWalk =>
        treeWalk.addTree(commit.getTree)
        treeWalk.setRecursive(true)
        if (!path.isEmpty) {
          treeWalk.setFilter(PathFilter.create(path))
        }
        if (treeWalk != null) {
          while (treeWalk.next()) {
            val entryPath =
              if (path.isEmpty) baseName + "/" + treeWalk.getPathString
              else path.split("/").last + treeWalk.getPathString.substring(path.length)
            val mode = treeWalk.getFileMode.getBits
            openFile(git, commit.getTree, treeWalk.getPathString) { in =>
              val tempFile = File.createTempFile(repositoryName + "-" + revision, ".archive")
              val size = Using.resource(new FileOutputStream(tempFile)) { out =>
                CompressIOUtils.copy(
                  EolStreamTypeUtil.wrapInputStream(
                    in,
                    EolStreamTypeUtil
                      .detectStreamType(
                        OperationType.CHECKOUT_OP,
                        git.getRepository.getConfig.get(WorkingTreeOptions.KEY),
                        treeWalk.getAttributes
                      )
                  ),
                  out
                )
              }

              val entry: ArchiveEntry = entryCreator(entryPath, size, date, mode)
              archive.putArchiveEntry(entry)
              Using.resource(new FileInputStream(tempFile)) { in =>
                CompressIOUtils.copy(in, archive)
              }
              archive.closeArchiveEntry()
              tempFile.delete()
            }
          }
        }
      }
    }
  }

  def createArchive(path: String, revision: String) = {
    val tempFile = File.createTempFile(repositoryName + "-" + revision, ".archive.zip")
    Using.resource(new ZipArchiveOutputStream(tempFile)) { zip =>
      archiveRepo(path, revision, zip) { (path, size, date, mode) =>
        val entry = new ZipArchiveEntry(path)
        entry.setSize(size)
        entry.setUnixMode(mode)
        entry.setTime(date.getTime)
        entry
      }
    }
    tempFile
  }
}
