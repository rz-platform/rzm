package repositories

sealed trait RzError

case object NotFoundInRepository extends RzError
case object ParsingError         extends RzError
case object AccessDenied         extends RzError
