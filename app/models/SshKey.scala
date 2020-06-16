package models

import anorm.{ Macro, ToParameterList }

case class SshKey(
  id: Int = 0,
  publicKey: String,
  title: Option[String]
)
object SshKey {
  implicit def toParameters: ToParameterList[SshKey] = Macro.toParameters[SshKey]
}
