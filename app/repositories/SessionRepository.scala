package repositories

import com.redis.serialization.Parse.Implicits.parseByteArray

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

/**
 * A session Repository that ties session id to secret key
 */
@Singleton
class SessionRepository @Inject() (r: Redis)(implicit ec: ExecutionContext) {
  def create(secretKey: Array[Byte]): Future[String] = Future {
    val sessionId = newSessionId()
    r.clients.withClient(client => client.set(sessionId, secretKey))
    sessionId
  }

  def lookup(sessionId: String): Future[Option[Array[Byte]]] = Future {
    r.clients.withClient(client => client.get[Array[Byte]](sessionId))
  }

  def put(sessionId: String, secretKey: Array[Byte]): Future[Unit] = Future {
    r.clients.withClient(client => client.set(sessionId, secretKey))
  }
  def delete(sessionId: String): Future[Unit] = Future {
    r.clients.withClient(client => client.del(sessionId))
  }

  private val sr = new java.security.SecureRandom()

  private def newSessionId(): String =
    new java.math.BigInteger(130, sr).toString(32)
}
