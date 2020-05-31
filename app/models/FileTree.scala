package models

import scala.collection.mutable.ArrayBuffer

class FileNode(nodeValue: String, path: String) {

  val folders = new ArrayBuffer[FileNode]()
  val files   = new ArrayBuffer[FileNode]()

  val data: String            = nodeValue
  val incrementalPath: String = path

  def isFile: Boolean = folders.isEmpty && files.isEmpty

  def isRoot: Boolean = data == "."

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
  val root: FileNode = r

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

case class Breadcrumb(name: String, path: String)

case class Breadcrumbs(fileName: String, breadcrumbs: Array[Breadcrumb])

object Breadcrumbs {
  private def splitPathIntoBreadcrumbs(path: String, isFile: Boolean = false): Array[Breadcrumb] =
    path match {
      case "." => Array()
      case _ =>
        val fullPath = DecodedPath(path).toString.split("/")
        val breadcrumbs = fullPath.zipWithIndex.map {
          case (element, index) =>
            Breadcrumb(element, fullPath.slice(0, index + 1).mkString("/"))
        }
        if (isFile) {
          breadcrumbs.dropRight(1) // we don't need a file name in path
        } else {
          breadcrumbs
        }
    }

  def apply(path: String, isFile: Boolean = false): Breadcrumbs =
    Breadcrumbs(DecodedPath(path).nameWithoutPath, splitPathIntoBreadcrumbs(path, isFile))
}
