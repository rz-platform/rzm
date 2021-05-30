package infrastructure

import com.redis._
import play.api.Configuration
import play.api.inject.ApplicationLifecycle

import javax.inject._
import scala.concurrent.Future

/**
 * Component holds a connection to a Redis database
 * It is important that there is only be one instance of that component
 *
 */
@Singleton
class Redis @Inject() (config: Configuration, lifecycle: ApplicationLifecycle) {
  private val host = config.get[String]("play.redis.host")
  private val port = config.get[Int]("play.redis.port")

  private val clients = new RedisClientPool(host, port)

  /**
   * Obtains an instance from this pool.
   */
  def withClient[T](body: RedisClient => T): T = clients.withClient(body)

  // close pool & free resources
  lifecycle.addStopHook(() => Future.successful(clients.close()))
}
