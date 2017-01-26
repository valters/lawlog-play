package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import services.TableOfContents

@Singleton
class LawController @Inject() ( appToc: TableOfContents ) extends Controller {

  def index( id: String ) = Action {
    val law = appToc.law( id )
    Ok( views.html.law( id, law ) )
  }

}
