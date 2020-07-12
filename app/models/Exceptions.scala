package models

sealed trait RepositoryAccessException
object RepositoryAccessException {
  class RepoDoesNotExist extends Exception("Repository does not exist") with RepositoryAccessException
  class AccessDenied     extends Exception("Access denied") with RepositoryAccessException
}
