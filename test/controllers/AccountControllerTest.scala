package controllers

import infrastructure.RzDateTime
import play.api.test.CSRFTokenHelper.addCSRFToken
import play.api.test.FakeRequest
import play.api.test.Helpers.{ await, defaultAwaitTimeout }

class AccountControllerTest extends GenericControllerTest {

  "AccountController" must {
    "Attempt to create user with bad name" in {
      val badUserNames =
        List("&", "!", "%", "киррилица", "with space", "^", "@", "--++", "::", "t)", "exceededlength" * 10)
      badUserNames.map { username =>
        val request = addCSRFToken(
          FakeRequest(routes.AccountController.saveAccount())
            .withFormUrlEncodedBody(
              "username"    -> username,
              "fullName"    -> getRandomString,
              "password"    -> getRandomString,
              "timezone"    -> RzDateTime.defaultTz.toString,
              "mailAddress" -> s"$getRandomString@rzm.dev"
            )
        )

        val result = await(accountController.saveAccount().apply(request))
        result.header.status must equal(400)
      }
    }

    "Successful attempt to create user" in {
      val goodUserNames = List(
        getRandomString,
        "with_underscore" + getRandomString.take(4),
        "with-minus" + getRandomString.take(4),
        "MiXeDcAse" + getRandomString.take(4)
      )
      goodUserNames.map { username =>
        val request = addCSRFToken(
          FakeRequest(routes.AccountController.saveAccount())
            .withFormUrlEncodedBody(
              "username"    -> username,
              "fullName"    -> getRandomString,
              "password"    -> getRandomString,
              "timezone"    -> RzDateTime.defaultTz.toString,
              "mailAddress" -> s"$getRandomString@rzm.dev"
            )
        )

        val result = await(accountController.saveAccount().apply(request))
        result.header.status must equal(303)

        val a = await(accountRepository.getByName(username.toLowerCase))
        a.isRight must equal(true)
      }
    }
  }

}
