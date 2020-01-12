package controllers

import java.util.Calendar
import javax.inject.{Inject, Singleton}
import models.{AccountLoginData, AccountRegistrationData, Account, AccountRepository}
import services._
import play.api.data.Forms._
import play.api.data._
import play.api.mvc._
import views._
import scala.concurrent.{ExecutionContext, Future}
import org.mindrot.jbcrypt.BCrypt

class AuthController @Inject()(accountService: AccountRepository,
                               cc: MessagesControllerComponents)(implicit ec: ExecutionContext)
  extends MessagesAbstractController(cc) {

  val loginForm = Form(
    mapping("userName" -> nonEmptyText,
      "password" -> nonEmptyText,
    )(AccountLoginData.apply)(AccountLoginData.unapply))

  val registerForm = Form(
    mapping(
      "userName" -> nonEmptyText,
      "fullName" -> optional(text),
      "password" -> nonEmptyText,
      "mailAddress" -> email,
    )(AccountRegistrationData.apply)(AccountRegistrationData.unapply)
  )

  def login = Action {
    implicit request =>
      Ok(html.userLogin(loginForm))
  }

  def register = Action {
    implicit request =>
      Ok(html.userRegister(registerForm))
  }

  def saveUser = Action.async {
    implicit request =>
      registerForm.bindFromRequest.fold(
        formWithErrors => Future(BadRequest(html.userRegister(formWithErrors))),
        user => {
          val acc = Account(0, user.userName, user.fullName, user.mailAddress, EncryptionService.getHash(user.password), isAdmin = false, Calendar.getInstance().getTime, None, isRemoved = false, None)
          accountService.insert(acc).map { accountId =>
            Redirect(routes.RepositoryController.list).withSession(AuthController.SESSION_NAME -> accountId.get.toString)
          }
        }
      )
  }

  def authenticate = Action.async {
    implicit request =>
      loginForm.bindFromRequest.fold(
        formWithErrors => Future(BadRequest(html.userLogin(formWithErrors))),
        user => {
          accountService.findByLoginOrEmail(user.userName).flatMap {
            case Some(account) =>
              if (EncryptionService.checkHash(user.password, account.password)) {
                Future(Redirect(routes.RepositoryController.list)
                  .withSession(AuthController.SESSION_NAME -> account.id.toString))
              } else {
                Future.successful(NotFound)
              }
            case other => Future.successful(NotFound)
          }
        }
      )
  }

  def logout = Action {
    Redirect(routes.AuthController.login).withNewSession.flashing(
      "success" -> "You are now logged out."
    )
  }
}
object AuthController {
  val SESSION_NAME = "user_id"
}
