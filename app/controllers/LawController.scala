package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import services.TableOfContents
import utils.DateParam
import utils.FileReader
import play.twirl.api.Html

@Singleton
class LawController @Inject() ( appToc: TableOfContents ) extends Controller {

  def index( id: String ) = Action {
    try {
      val law = appToc.law( id )
      val currVer = law.versions.head
      val diffVer = DateParam.eurToIso(currVer)
      val diffContent = Html( FileReader.readFile( law.diffFor( diffVer ) ) )
      Ok( views.html.law( id, law, currVer, diffVer, diffContent ) )
    }
    catch {
      case e: NoSuchElementException =>
        NotFound( s"Unrecognized: $id" )
    }
  }

  def version( id: String, ver: String ) = Action {
      val law = appToc.law( id )
      val currVer = DateParam.isoToEur(ver)
      val diffVer = DateParam.eurToIso(currVer)
      val diffContent = Html( FileReader.readFile( law.diffFor( diffVer ) ) )
      Ok( views.html.law( id, law, currVer, diffVer, diffContent ) )
  }
}
