package controllers

import javax.inject._
import play.api.mvc._
import services.TableOfContents
import play.twirl.api.Html

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject() ( appToc: TableOfContents ) extends Controller {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index = Action {
    Ok( views.html.index( appToc ) )
  }

  def sandbox = Action {
    Ok( views.html.sandbox( "testing" ) )
  }

}
