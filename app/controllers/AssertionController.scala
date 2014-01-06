package controllers

import oxalis.security.Secured
import models.security.Role
import models.assertion.Assertion
import views.html
import play.api.libs.json.Json
import oxalis.security._

object AssertionController extends Controller with Secured {

  val DefaultAccessRole = Role.User

  def log = UserAwareAction(parser = parse.urlFormEncoded) { implicit request =>
    for {
      value <- postParameter("value") ?~ "Value is missing"
      globalContext <- postParameter("globalContext") ?~ "globalContext is missing"
      localContext <- postParameter("localContext") ?~ "localContext is missing"
      message <- postParameter("message") ?~ "message is missing"
      stacktrace <- postParameter("stacktrace") ?~ "stacktrace is missing"
      title <- postParameter("title") ?~ "title is missing"
    } yield {
      val a = Assertion(request.userOpt.map(_._id), System.currentTimeMillis(), value, title, message, stacktrace, globalContext, localContext)
      Assertion.insert(a)
      Ok
    }
  }

  def list = Authenticated(role = Role.Admin) { implicit request =>
    Ok(html.admin.assertion.assertionList(Assertion.findAll.sortBy(-_.timestamp)))
  }

  def listSliced(from: Int, number: Int) = Authenticated(role = Role.Admin) { implicit request =>
    
    val assertions = Assertion.findAll.sortBy(-_.timestamp).drop(from).take(number)
    Ok(html.admin.assertion.assertionList(assertions))
  }

  def view(assertionId: String) = Authenticated(role = Role.Admin) { implicit request =>
    for {
      assertion <- Assertion.findOneById(assertionId) ?~ "Assertion not found."
    } yield {
      Ok(html.admin.assertion.assertion(assertion))
    }
  }
}