package users.errors

sealed trait ImageProcessingError extends Throwable
case object WrongContentType      extends ImageProcessingError
case object ExceededMaxSize       extends ImageProcessingError
