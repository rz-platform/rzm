package infrastructure.repositories

import org.apache.commons.io.IOUtils
import org.apache.commons.io.input.BOMInputStream
import org.apache.tika.Tika
import org.mozilla.universalchardet.UniversalDetector

object FileSystem {
  private def defining[A, B](value: A)(f: A => B): B = f(value)

  def getMimeType(name: String): String =
    defining(new Tika()) { tika =>
      tika.detect(name) match {
        case null     => "application/octet-stream"
        case mimeType => mimeType
      }
    }
  def convertFromByteArray(content: Array[Byte]): String =
    IOUtils.toString(new BOMInputStream(new java.io.ByteArrayInputStream(content)), detectEncoding(content))

  def detectEncoding(content: Array[Byte]): String =
    defining(new UniversalDetector(null)) { detector =>
      detector.handleData(content, 0, content.length)
      detector.dataEnd()
      detector.getDetectedCharset match {
        case null => "UTF-8"
        case e    => e
      }
    }

  def getSafeMimeType(name: String): String =
    getMimeType(name)
      .replace("text/html", "text/plain")
      .replace("image/svg+xml", "text/plain; charset=UTF-8")

}
