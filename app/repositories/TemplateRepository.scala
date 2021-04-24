package repositories

import models._
import play.api.Configuration
import templates.TemplateParser

import java.io.File
import javax.inject.Inject
import scala.collection.SortedMap

class TemplateRepository @Inject() (config: Configuration) {
  private val logger = play.api.Logger(this.getClass)

  // templates directory root
  val dir = new File(config.get[String]("play.server.templates.dir"))

  def get(name: String): Option[Template] = list.get(name)

  def list: SortedMap[String, Template] =
    if (dir.exists && dir.isDirectory) {
      val l: Array[Template] =
        dir.listFiles.filter(filter).map(file => TemplateParser.parse(file)).sortBy(t => t.name)
      SortedMap.from(l.map(t => Tuple2(t.id, t)))
    } else {
      logger.warn("Template folder does not exist")
      SortedMap()
    }

  private def filter(f: File): Boolean =
    f.isDirectory && f.getName.take(1) != FileNames.root && f.getName.takeRight(1) != "~"
}
