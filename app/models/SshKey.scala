package models

import anorm.{ Macro, ToParameterList }

case class SshKey(
  id: Int = 0,
  publicKey: String
)
object SshKey {
  implicit def toParameters: ToParameterList[SshKey] = Macro.toParameters[SshKey]
}

case class SshKeyData(
  publicKey: String
)
case class SshRemoveData(
  id: Int
)
