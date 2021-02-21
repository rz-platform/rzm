package repositories

sealed trait RzError

case object NotFoundInRepository extends RzError
case object ParsingError         extends RzError
case object AccessDenied         extends RzError

case object TemplateError extends RzError

trait FileUploadException
class WrongContentType extends Exception with FileUploadException
class ExceededMaxSize  extends Exception with FileUploadException
