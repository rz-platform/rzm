package models

sealed trait AuthException
object AuthException {
  class UserDoesNotExist extends Exception with AuthException
  class WrongPassword    extends Exception with AuthException
}

sealed trait RepositoryAccessException
object RepositoryAccessException {
  class RepoDoesNotExist extends Exception("Repository does not exist") with RepositoryAccessException
  class AccessDenied     extends Exception("Access denied") with RepositoryAccessException
}
