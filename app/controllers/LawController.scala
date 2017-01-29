package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import services.TableOfContents
import utils.DateParam
import utils.FileReader
import play.twirl.api.Html
import play.api.libs.json.Json
import play.api.libs.json.JsString
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue

@Singleton
class LawController @Inject() ( appToc: TableOfContents ) extends Controller {

  def index( id: String ) = Action {
    try {
      val law = appToc.law( id )
      law.isoVersions match {
        case Nil => NotFound( s"No diffs found for law: $id" )

        case (currVer,diffVer) +: _ => { // take seq [vector] head (don't care about rest), explode tuple into variables
          renderVersion( id, law, currVer, diffVer )
        }
      }
    }
    catch {
      case e: NoSuchElementException =>
        NotFound( s"Unrecognized law: $id" )
    }
  }

  def version( id: String, ver: String ) = Action {
      val law = appToc.law( id )
      val currVer = DateParam.isoToEur(ver)
      val diffVer = DateParam.eurToIso(currVer)
      renderVersion( id, law, currVer, diffVer )
  }

  /** ajax request support */
  def rawVersion( id: String, ver: String ) = Action {
      val law = appToc.law( id )
      val currVer = DateParam.isoToEur(ver)
      val diffVer = DateParam.eurToIso(currVer)
      val diffContent: String = law.diffContent( law.diffFileFor( diffVer ) )
      //val jsonContent: JsValue = JsObject( Seq( "diff" -> JsString( diffContent ) ) )
      Ok( Html( diffContent ) )
  }

  def renderVersion(id: String, law: services.LawMetadata, currVer: String, diffVer: String): Result = {
    val diffContent = Html( law.diffContent( law.diffFileFor( diffVer ) ) )
    Ok( views.html.law( id, law, currVer, diffVer, diffContent ) )
  }

}
