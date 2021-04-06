package models

import scala.collection.mutable.ArrayBuffer

case class FileNode(data: String, incrementalPath: String) {
  val folders = new ArrayBuffer[FileNode]()
  val files   = new ArrayBuffer[FileNode]()

  val pathWithoutRoot: String = incrementalPath.replaceFirst("./", "")

  val pathAsUrl: String = RzPathUrl.encodeUri(pathWithoutRoot)

  val hash: String = incrementalPath.hashCode.toString

  val isRoot: Boolean = data == FileNames.root

  def isFile: Boolean = folders.isEmpty && files.isEmpty

  private def realDepth: Int = pathWithoutRoot.count(_ == '/')

  def depth: Int = if (isRoot) 0 else if (realDepth < FileTree.maxDepth) realDepth else FileTree.maxDepth

  def addElement(currentPath: String, list: Array[String]): Unit = {
    val currentChild = new FileNode(list.head, currentPath + "/" + list(0))

    if (list.length == 1) {
      files += currentChild
    } else {
      val index = folders.indexOf(currentChild)
      if (index == -1) {
        folders += currentChild
        currentChild.addElement(currentChild.incrementalPath, list.slice(1, list.length))
      } else {
        val nextChild = folders(index)
        nextChild.addElement(currentChild.incrementalPath, list.slice(1, list.length))
      }
    }
  }

  override def equals(obj: Any): Boolean = {
    val cmpObj = obj.asInstanceOf[FileNode]
    incrementalPath == cmpObj.incrementalPath && data == cmpObj.data
  }
}

/**
 * Represent filesystem (files/dir) from list of paths
 *
 * @param root node
 */
case class FileTree(root: FileNode) {
  def addElement(elementValue: String): Unit = root.addElement(root.incrementalPath, elementValue.split("/"))
}

object FileTree {
  val excluded: Array[String] = Array(FileNames.keep, FileNames.root)
  val maxDepth                = 4
}
