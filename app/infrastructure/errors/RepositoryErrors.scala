package infrastructure.errors

sealed trait RepositoryError extends Throwable

case object NotFoundInRepository extends RepositoryError
case object ParsingError         extends RepositoryError
case object AccessDenied         extends RepositoryError
