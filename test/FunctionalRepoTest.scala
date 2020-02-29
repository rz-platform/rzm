import controllers.{RepositoryController, routes}
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.mvc.Result
import play.api.test.CSRFTokenHelper._
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Future

class FunctionalRepoTest extends PlaySpec with GuiceOneAppPerSuite with Injecting with ScalaFutures {
  "RepositoryController" must {
    "Create Repository" in {
      // Pull the controller from the already running Play application, using Injecting
      val controller = inject[RepositoryController]
      //
      //      // Call using the FakeRequest and the correct body information and CSRF token
      val request = addCSRFToken(FakeRequest(routes.RepositoryController.saveRepository())
        .withFormUrlEncodedBody("name" -> "foo", "description" -> "test")
        .withSession(("user_id", "1")))

      val futureResult: Future[Result] = controller.saveRepository().apply(request)

      whenReady(futureResult) { result =>
        result.header.headers(LOCATION) must equal(routes.RepositoryController.list)
      }
    }
  }

}
