package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import java.io.File
import java.io.FileInputStream
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import services.TableOfContents

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject() (likumi: TableOfContents) extends Controller {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index = Action {
    Ok( views.html.index( "Your new application is ready.", likumi.likums() ) )
  }

}


