package repositories

import akka.actor.ActorSystem
import play.api.libs.concurrent.CustomExecutionContext

import javax.inject._

/**
 * This class is a pointer to an execution context configured to point to "database.dispatcher"
 * in the "application.conf" file.
 */
@Singleton
class DatabaseExecutionContext @Inject() (system: ActorSystem)
    extends CustomExecutionContext(system, "database.dispatcher")
