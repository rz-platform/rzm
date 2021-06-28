package templates.services

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.cache.ConcurrentMapTemplateCache
import com.github.jknack.handlebars.io.FileTemplateLoader
import documents.models.{ CommitFile, FilePath }
import templates.models.Template
import templates.repositories.TemplateRepository

import java.io.File
import java.nio.charset.StandardCharsets
import java.util
import javax.inject.Inject
import scala.collection.immutable.Map
import scala.jdk.CollectionConverters._
import scala.util.Try

class TemplateRendererService @Inject() (templateRepository: TemplateRepository) {
  private val logger = play.api.Logger(this.getClass)

  private val loader     = new FileTemplateLoader(templateRepository.dir.toString, "")
  private val cache      = new ConcurrentMapTemplateCache()
  private val handlebars = new Handlebars(loader).`with`(cache)

  val supportedTextExtensions = List("txt", "tex", "json", "bib")

  /*
   * Compile templates.
   * The implementation uses a cache for previously compiled Templates. By default,
   * if the resource has been compiled previously, and no changes have occurred
   * since in the resource, compilation will be skipped and the previously created
   * Template will be returned.
   */
  def compile(tplList: Iterable[Template]): Unit = tplList.map { tpl =>
    tpl.files.filter(canRender).map(f => Try(handlebars.compile(relativeFilePath(tpl, f))))
  }

  def render(tpl: Template, context: Map[String, String]): Seq[CommitFile] =
    tpl.files.map { f: File =>
      val filepath = relativeFilePath(tpl, f)
      if (canRender(filepath)) {
        renderFile(filepath, context, f)
      } else {
        CommitFile.fromFile(f, tpl.path.toPath)
      }
    }.toSeq

  private def canRender(path: String): Boolean = supportedTextExtensions contains FilePath.extension(path)
  private def canRender(file: File): Boolean   = supportedTextExtensions contains FilePath.extension(file.getName)

  private def renderFile(path: String, context: Map[String, String], file: File): CommitFile = {
    // compilation will be skipped
    // if the resource has been compiled previously
    val template = handlebars.compile(path)
    val content  = template.apply(ctxToJava(context)).getBytes(StandardCharsets.UTF_8)
    CommitFile(file.getAbsolutePath, file.getName, content)
  }

  private def relativeFilePath(tpl: Template, f: File): String = {
    val folder   = tpl.path.getName
    val relative = FilePath.relativize(tpl.path.toPath, f.getAbsolutePath)
    val name     = f.getName
    relative match {
      case "." => folder + "/" + name
      case _   => folder + "/" + relative + "/" + name
    }
  }

  private def ctxToJava(context: Map[String, String]): util.Map[String, String] = Map.from(context).asJava
}
