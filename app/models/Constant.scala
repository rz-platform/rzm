package models

import java.io.File
import scala.util.matching.Regex


object ForbiddenSymbols {
  private val pathForbiddenSymbols: List[String]    = List("?", ":", "#", "&", "..", "$", "%")
  private val generalForbiddenSymbols: List[String] = pathForbiddenSymbols :+ "/"

  def isPathValid(itemName: String): Boolean = pathForbiddenSymbols.exists(itemName contains _)

  def isNameValid(itemName: String): Boolean = generalForbiddenSymbols.exists(itemName contains _)

  override def toString: String = generalForbiddenSymbols.mkString("") // for testing purposes
}

object TemplateExcluded {
  val excludedExt = List("pdf")
  val excluded    = List("schema.json")

  def filter(file: File): Boolean = {
    val name = file.getName
    val ext  = FilePath.extension(name)
    file match {
      case _ if file.isDirectory          => false
      case _ if excludedExt.contains(ext) => false
      case _ if excluded.contains(name)   => false
      case _                              => true
    }
  }
}

object RzRegex {
  val onlyAlphabet: Regex = "^[A-Za-z\\d_\\-]+$".r
}

sealed trait RepositoryPage
case object FileViewPage      extends RepositoryPage
case object CollaboratorsPage extends RepositoryPage
case object CommitHistoryPage extends RepositoryPage
case object FileUploadPage    extends RepositoryPage
case object NewFilePage       extends RepositoryPage
case object ConstructorPage   extends RepositoryPage
