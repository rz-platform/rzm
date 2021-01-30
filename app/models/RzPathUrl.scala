package models

import java.io.File
import java.net.{ URLDecoder, URLEncoder }
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

case class RzPathUrl(uri: String) {
  lazy val nameWithoutPath: String = new File(uri).getName

  lazy val pathWithoutFilename: String =
    try {
      Paths.get(uri).getParent.toString
    } catch {
      case _: java.lang.NullPointerException => "." // if path is a filename, we are in root directory
    }

  lazy val encoded: String = RzPathUrl.encodeUri(uri)
}

object RzPathUrl {
  private def decode(key: String): String =
    try {
      URLDecoder.decode(key.replace("+", " "), "utf-8")
    } catch {
      case _: java.lang.IllegalArgumentException => ""
    }

  def make(path: String): RzPathUrl = RzPathUrl(decode(path))

  def make(path: String, name: String, isFolder: Boolean): RzPathUrl = {
    val p = decode(path) match {
      case "." if isFolder  => Paths.get(decode(name), ".gitkeep").toString
      case "." if !isFolder => decode(name)
      case p if isFolder    => Paths.get(p, name, ".gitkeep").toString
      case p if !isFolder   => Paths.get(p, name).toString
    }

    RzPathUrl(p)
  }

  def encodeUri(path: String): String =
    path
      .split("/")
      .map(word => URLEncoder.encode(word, StandardCharsets.UTF_8))
      .mkString("/")
}
