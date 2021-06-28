package templates.errors

trait TemplateError        extends Throwable
case object TemplateError  extends TemplateError
case object JsonParseError extends TemplateError
case object FileNotFound   extends TemplateError
