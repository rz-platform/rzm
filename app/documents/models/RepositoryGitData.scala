package documents.models

import org.eclipse.jgit.revwalk.RevCommit

case class RepositoryGitData(files: List[FileInfo], lastCommit: Option[RevCommit])
