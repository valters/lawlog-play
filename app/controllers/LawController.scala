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

  def renderVersion(id: String, law: services.LawMetadata, currVer: String, diffVer: String): Result = {
    val diffContent = Html( law.diffContent( law.diffFileFor( diffVer ) ) )
    Ok( views.html.law( id, law, currVer, diffVer, diffContent ) )
  }

}
