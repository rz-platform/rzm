package repositories

import com.redis._
import play.api.Configuration

import javax.inject._

@Singleton
class Redis @Inject() (config: Configuration) {
  private val host = config.get[String]("play.redis.host")
  private val port = config.get[Int]("play.redis.port")

  val clients = new RedisClientPool(host, port)
}
