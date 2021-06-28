package documents.models

case class EditedItem(content: String, rev: String, path: String, name: String)

case class UploadFileForm(path: String)

case class NewItem(name: String, rev: String, path: String, isFolder: Boolean)
