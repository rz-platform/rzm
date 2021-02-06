package encryption
import models.{ Auth, UserInfo }
import play.api.http.SecretConfiguration

import javax.inject.{ Inject, Singleton }
import scala.concurrent.duration._

/**
 * Creates a cookie baker with the given secret key.
 */
@Singleton
class UserInfoCookieBakerFactory @Inject() (
  encryptionService: EncryptionService,
  secretConfiguration: SecretConfiguration
) {
  def createCookieBaker(secretKey: Array[Byte]): EncryptedCookieBaker[UserInfo] =
    new EncryptedCookieBaker[UserInfo](secretKey, encryptionService, secretConfiguration) {
      // This can also be set to the session expiration, but lets keep it around for example
      override val expirationDate: FiniteDuration = 365.days
      override val COOKIE_NAME: String            = Auth.USER_INFO_COOKIE_NAME
    }
}
