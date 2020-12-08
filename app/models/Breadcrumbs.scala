package models

case class Breadcrumb(name: String, path: String)

sealed trait FileTreeBreadcrumbs {
  def fileName: String
}
case class Breadcrumbs(fileName: String, breadcrumbs: Array[Breadcrumb]) extends FileTreeBreadcrumbs
case class EmptyBreadcrumbs(fileName: String)                            extends FileTreeBreadcrumbs

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
