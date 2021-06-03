package services.encryption
import models.{ Auth, AccountInfo }
import play.api.http.SecretConfiguration

import javax.inject.Inject
import scala.concurrent.duration._

/**
 * Creates a cookie baker with the given secret key.
 */
class UserInfoCookieBakerFactory @Inject() (
  encryptionService: EncryptionService,
  secretConfiguration: SecretConfiguration
) {
  def createCookieBaker(secretKey: Array[Byte]): EncryptedCookieBaker[AccountInfo] =
    new EncryptedCookieBaker[AccountInfo](secretKey, encryptionService, secretConfiguration) {
      // This can also be set to the session expiration, but lets keep it around for example
      override val expirationDate: FiniteDuration = 365.days
      override val COOKIE_NAME: String            = Auth.userInfoCookie
    }
}
