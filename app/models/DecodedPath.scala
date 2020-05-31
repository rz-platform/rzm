package models

import java.io.File
import java.net.URLDecoder
import java.nio.file.Paths

case class DecodedPath(uri: String, cleared: Boolean) {
  override def toString: String = uri

  def nameWithoutPath: String = new File(uri).getName

  def pathWithoutFilename: String =
    try {
      Paths.get(uri).getParent.toString
    } catch {
      case _: java.lang.NullPointerException => "." // if path is a filename, we are in root directory
    }
}

object DecodedPath {
  private def decodeNameFromUrl(key: String): String = URLDecoder.decode(key.replace("+", " "), "utf-8")

  def apply(uri: String): DecodedPath = DecodedPath(decodeNameFromUrl(uri), cleared = true)

  def apply(path: String, name: String, isFolder: Boolean): DecodedPath = {
    val decodedName = decodeNameFromUrl(name)
    val decodedPath = decodeNameFromUrl(path)

    val p = path match {
      case "." if isFolder  => Paths.get(decodedName, ".gitkeep").toString
      case "." if !isFolder => decodedName
      case _ if isFolder    => Paths.get(decodedPath, name, ".gitkeep").toString
      case _ if !isFolder   => Paths.get(decodedPath, name).toString
    }

    DecodedPath(p)
  }
}