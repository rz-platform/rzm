package controllers

import akka.actor.ActorSystem
import models.{ Account, AccountRegistrationData, RzRepository, SessionName }
import org.scalatest.{ BeforeAndAfterAll, PrivateMethodTester }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.mvc.Result
import play.api.test.CSRFTokenHelper.addCSRFToken
import play.api.test.Helpers.await
import play.api.test.{ FakeRequest, Injecting }
import repositories.{ AccountRepository, GitRepository, RzGitRepository }

class GenericControllerTest
    extends PlaySpec
    with BeforeAndAfterAll
    with GuiceOneAppPerSuite
    with Injecting
    with PrivateMethodTester
    with ScalaFutures {

  implicit val sys: ActorSystem = ActorSystem("RepositoryTest")
  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  private val logger = play.api.Logger(this.getClass)

  implicit val sys: ActorSystem = ActorSystem("RepositoryTest")

  def getRandomString: String =
    java.util.UUID.randomUUID.toString.replace("-", "")

  def config: Configuration = app.injector.instanceOf[Configuration]

  def accountController: AccountController          = app.injector.instanceOf[AccountController]
  def gitEntitiesRepository: RzRepositoryController = app.injector.instanceOf[RzRepositoryController]
  def git: GitRepository                            = app.injector.instanceOf[GitRepository]

  val sessionName: String = SessionName.toString

  def createAccount(): Account = {
    val data =
      AccountRegistrationData(getRandomString, Some(getRandomString), getRandomString, s"$getRandomString@rzm.dev")
    val request = addCSRFToken(
      FakeRequest(routes.AccountController.saveAccount())
        .withFormUrlEncodedBody(
          "userName"    -> data.userName,
          "fullName"    -> data.fullName.get,
          "password"    -> data.password,
          "mailAddress" -> data.email
        )
    )
    await(accountController.saveAccount().apply(request))
    new Account(data)
  }

  def createRepository(
    name: String,
    owner: Account
  ): (Result, RzRepository) = {
    val request = addCSRFToken(
      FakeRequest(routes.RzRepositoryController.saveRepository())
        .withFormUrlEncodedBody("name" -> name, "description" -> getRandomString)
        .withSession((sessionName, owner.id))
    )
    val result = await(gitEntitiesRepository.saveRepository().apply(request))
    (result, new RzRepository(owner, name))
  }

}
