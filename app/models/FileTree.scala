package models

import scala.collection.mutable.ArrayBuffer

class FileNode(nodeValue: String, path: String) {
  val folders = new ArrayBuffer[FileNode]()
  val files   = new ArrayBuffer[FileNode]()

  val data: String            = nodeValue
  val incrementalPath: String = path
  val pathWithoutRoot: String = path.replaceFirst("./", "")

  val isRoot: Boolean = data == FileRoot.toString

  def isFile: Boolean = folders.isEmpty && files.isEmpty

  val pathWithoutRootEncoded: String = EncodedPath.fromString(pathWithoutRoot)
  def depth: Int = pathWithoutRoot.count(_ == '/') match {
    case x if !isRoot                      => x
    case _ if isRoot                       => 0
    case x if x > MaxDepthInFileTree.toInt => MaxDepthInFileTree.toInt
  }

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
 * @param r node
 */
class FileTree(r: FileNode) {
  var commonRoot: FileNode = _
  val root: FileNode       = r

  def addElement(elementValue: String): Unit = root.addElement(root.incrementalPath, elementValue.split("/"))

  def getCommonRoot: FileNode =
    if (commonRoot != null)
      commonRoot
    else {
      var current = root
      while (current.files.length <= 0) {
        current = current.folders(0)
      }
      commonRoot = current
      commonRoot
    }
}
