package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import services.TableOfContents
import utils.DateParam

@Singleton
class LawController @Inject() ( appToc: TableOfContents ) extends Controller {

  def index( id: String ) = Action {
    try {
      val law = appToc.law( id )
      val versions = appToc.versions( id )
      val currVer = versions.head
      val diffVer = DateParam.eurToIso(currVer)
      Ok( views.html.law( id, law, versions, currVer, diffVer ) )
    }
    catch {
      case e: NoSuchElementException =>
        NotFound( s"Unrecognized: $id" )
    }
  }

  def version( id: String, ver: String ) = Action {
      val law = appToc.law( id )
      val versions = appToc.versions( id )
      val currVer = DateParam.isoToEur(ver)
      val diffVer = DateParam.eurToIso(currVer)
      Ok( views.html.law( id, law, versions, currVer, diffVer ) )
  }
}
