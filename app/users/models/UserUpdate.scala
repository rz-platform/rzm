package users.models

case class UserUpdate(username: String, fullName: Option[String], email: String)

case class PasswordData(oldPassword: String, newPassword: String)

case class TimeZoneData(tz: String)
