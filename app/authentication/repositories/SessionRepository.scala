package authentication.repositories

import com.redis.serialization.Parse.Implicits.parseByteArray
import infrastructure.repositories.Redis

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

/**
 * A session Repository that ties session id to secret key
 */
class SessionRepository @Inject() (redis: Redis)(implicit ec: ExecutionContext) {
  def create(secretKey: Array[Byte]): Future[String] = Future {
    val sessionId = newSessionId()
    redis.withClient(client => client.set(sessionId, secretKey))
    sessionId
  }

  def lookup(sessionId: String): Future[Option[Array[Byte]]] = Future {
    redis.withClient(client => client.get[Array[Byte]](sessionId))
  }

  def put(sessionId: String, secretKey: Array[Byte]): Future[Unit] = Future {
    redis.withClient(client => client.set(sessionId, secretKey))
  }
  def delete(sessionId: String): Future[Unit] = Future {
    redis.withClient(client => client.del(sessionId))
  }

  private val sr = new java.security.SecureRandom()

  private def newSessionId(): String =
    new java.math.BigInteger(130, sr).toString(32)
}
