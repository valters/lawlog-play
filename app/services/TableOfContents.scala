package services

import java.io.File
import java.io.FileInputStream

import javax.inject.Singleton
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsLookup
import play.api.libs.json.JsObject
import scala.collection.Set
import play.api.libs.json.Reads
import play.api.libs.json.JsPath
import play.api.libs.functional.syntax._
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError

/** component is injected into a controller */
trait TableOfContents {
  def likums(): String
}

case class LawMetadata( url: String, print_id: String, desc: String )

/**
 * This class is a concrete implementation of the trait.
 */
@Singleton
class JsonTableOfContents extends TableOfContents {
  private val json: JsValue = readJson()

  implicit val lawReads = Json.reads[LawMetadata]

  def readJson(): JsValue = {
    val stream = new FileInputStream( new File( "app/assets/toc.json" ) )
    val json: JsValue = try { Json.parse( stream ) } finally { stream.close() }
    ( json \ "likumi" ).get
  }

  lazy val keys = json.as[JsObject].fieldSet.map( t => t._1 )

  override def likums(): String = ( json \ "satversme" \ "desc" ).get.as[String]

  def law( key: String ): Option[LawMetadata] = {
    val jsResult = ( json \ key ).validate[LawMetadata]
    jsResult match {
      case s: JsSuccess[LawMetadata] => Some( s.get )
      case e: JsError => println("Errors: " + JsError.toJson(e).toString()); None
    }
  }

  def getLaw( key: String ): LawMetadata = {
    val jsResult = ( json \ key ).validate[LawMetadata]
    jsResult match {
      case s: JsSuccess[LawMetadata] => s.get
      case e: JsError => throw new RuntimeException("Errors: " + JsError.toJson(e).toString())
    }
  }

  // convert json to Map by binding key to corresponding LawMetadata: ultimately toMap converts our list of tuples into hash map
  lazy val laws: Map[String,LawMetadata] = keys.map( key => ( key, law( key ).get ) ).toMap

}
