package users.models

case class UserRegistration(
  username: String,
  fullName: Option[String],
  password: String,
  timezone: String,
  email: String
)
