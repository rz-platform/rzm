import java.sql.Connection

import controllers.{RepositoryController, routes}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.db.DBApi
import play.api.db.evolutions.Evolutions
import play.api.mvc.Result
import play.api.test.CSRFTokenHelper._
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Future

class FunctionalRepoTest
    extends PlaySpec
    with BeforeAndAfterAll
    with GuiceOneAppPerSuite
    with Injecting
    with ScalaFutures {
  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  def getRandomString: String = {
    java.util.UUID.randomUUID.toString
  }

  lazy val databaseApi: DBApi = inject[DBApi]

  override def beforeAll(): Unit = {
    Evolutions.applyEvolutions(databaseApi.database("default"))
  }

  override def afterAll(): Unit = {
    Evolutions.cleanupEvolutions(databaseApi.database("default"))
  }

  def getDbConnection: Connection = {
    databaseApi.database("default").getConnection()
  }

  def createUser(): Int = {
    getDbConnection
      .prepareStatement(
        "insert into account (userName, mailAddress, password, isAdmin, isRemoved) " +
          s"values ('$getRandomString', '$getRandomString', '$getRandomString', false, false)"
      )
      .executeUpdate()
  }

  "RepositoryController" must {
    "Create Repository" in {
      val userId = createUser().toString
      val repoId = getRandomString

      val controller = inject[RepositoryController]

      val request = addCSRFToken(
        FakeRequest(routes.RepositoryController.saveRepository())
          .withFormUrlEncodedBody("name" -> repoId, "description" -> getRandomString)
          .withSession(("user_id", userId))
      )

      val futureResult: Future[Result] = controller.saveRepository().apply(request)

      whenReady(futureResult) { result =>
        result.header.headers(LOCATION) must equal(routes.RepositoryController.list().toString)

        val isRepoExist = getDbConnection
          .prepareStatement(s"select 1 FROM repository WHERE name='$repoId'")
          .executeQuery()
        isRepoExist.next() must equal(true)
      }
    }
  }
}
