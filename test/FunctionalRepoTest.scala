import java.sql.{Connection, Statement}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.ByteString
import controllers.{RepositoryController, routes}
import git.GitRepository
import models.{Account, Repository, RepositoryGitData}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.Configuration
import play.api.db.DBApi
import play.api.db.evolutions.Evolutions
import play.api.libs.streams.Accumulator
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

  def createUser(): Account = {
    val userName = getRandomString
    val userPreparedStatement = getDbConnection
      .prepareStatement(
        "insert into account (userName, mailAddress, password, isAdmin, isRemoved) " +
          s"values ('$userName', '$getRandomString', '$getRandomString', false, false)",
        Statement.RETURN_GENERATED_KEYS
      )
    userPreparedStatement.executeUpdate()
    val rs = userPreparedStatement.getGeneratedKeys
    rs.next()
    Account(rs.getInt(1), userName, null, null, null, false, null)
  }

  "RepositoryController" must {
    "Create Repository" in {
      val account = createUser()
      val repoId = getRandomString

      val controller = inject[RepositoryController]

      val request = addCSRFToken(
        FakeRequest(routes.RepositoryController.saveRepository())
          .withFormUrlEncodedBody("name" -> repoId, "description" -> getRandomString)
          .withSession(("user_id", account.id.toString))
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

    "Create file in repository" in {
      implicit val sys = ActorSystem("MyTest")
      implicit val mat = ActorMaterializer()
      val account = createUser()
      val repoId = getRandomString

      val config = inject[Configuration]
      val controller = inject[RepositoryController]

      val request = addCSRFToken(
        FakeRequest(routes.RepositoryController.saveRepository())
          .withFormUrlEncodedBody("name" -> repoId, "description" -> getRandomString)
          .withSession(("user_id", account.id.toString))
      )

      await(controller.saveRepository().apply(request))
      val logger = play.api.Logger(this.getClass)

      val newFileId = getRandomString
      val answer: Accumulator[ByteString, Result] = controller
        .addNewItem(account.userName, repoId, ".", isFolder = false)
        .apply(
          addCSRFToken(
            FakeRequest(routes.RepositoryController.addNewItem(account.userName, repoId, ".", isFolder = false))
              .withFormUrlEncodedBody("name" -> newFileId)
              .withSession(("user_id", account.id.toString))
          )
        )

      val runResult: Future[Result] = answer.run()
      val opa: Result = await(runResult)
      logger.info(opa.toString)
      val git = new GitRepository(account, repoId, config.get[String]("play.server.git.path"))
      val gitData = git
        .fileList(Repository(0, repoId, true, null, "master", null, null), path = ".")
        .getOrElse(RepositoryGitData(List(), None))

      logger.info(gitData.files.toString())
      logger.info(newFileId)
      gitData.files.exists(_.name contains newFileId) must equal(true)
    }
  }
}
