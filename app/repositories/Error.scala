package repositories

sealed trait RzError extends Throwable

case object NotFoundInRepository extends RzError
case object ParsingError         extends RzError
case object AccessDenied         extends RzError

case object TemplateError  extends RzError
case object JsonParseError extends RzError
case object FileNotFound   extends RzError

sealed trait ImageProcessingError extends Throwable
case object WrongContentType      extends ImageProcessingError
case object ExceededMaxSize       extends ImageProcessingError
