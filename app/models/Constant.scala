package models

sealed trait RepositoryPage

case object FileViewPage      extends RepositoryPage
case object CollaboratorsPage extends RepositoryPage
case object CommitHistoryPage extends RepositoryPage
case object FileUploadPage    extends RepositoryPage
case object NewFilePage       extends RepositoryPage
case object ConstructorPage   extends RepositoryPage
