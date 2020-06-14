package models
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object EncodedPath {
  def fromString(path: String): String =
    path
      .split("/")
      .map(word => URLEncoder.encode(word, StandardCharsets.UTF_8))
      .mkString("/")
}
