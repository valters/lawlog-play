package services

import java.io.File
import java.io.FileInputStream

import scala.collection.mutable.HashMap

import javax.inject.Singleton
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

/** component is injected into a controller */
trait TableOfContents {
  /** list of law keys available */
  def keys(): Seq[String]
  /** get full metadata */
  def law( key: String ): LawMetadata
  /** @param key law short-id */
  def versions( key: String ): Seq[String]

  /** for testing */
  def pamatlikums(): String
}

case class LawMetadata( url: String, print_id: String, desc: String )

/**
 * This class is a concrete implementation of the trait.
 */
@Singleton
class JsonTableOfContents extends TableOfContents {
  protected val AssetRoot = "app/assets"
  protected val TocJsonAsset = AssetRoot+"/toc.json"
  protected val RootKey = "likumi"
  protected val Pamatlikums = "satversme"
  /** law version info is stored here */
  protected val VersionsRoot = AssetRoot+"/versijas"

  protected val json: JsValue = readJson()

  implicit val lawReads = Json.reads[LawMetadata]

  def readJson(): JsValue = {
    val stream = new FileInputStream( new File( TocJsonAsset ) )
    val json: JsValue = try { Json.parse( stream ) } finally { stream.close() }
    ( json \ RootKey ).get
  }

  /** preserve original key order */
  lazy val keys: Seq[String] = json.as[JsObject].fields.map( t ⇒ t._1 )

  def validateLaw( key: String ): Option[LawMetadata] = {
    val jsResult = ( json \ key ).validate[LawMetadata]
    jsResult match {
      case s: JsSuccess[LawMetadata] ⇒ Some( s.get )
      case e: JsError ⇒ println( "Errors: "+JsError.toJson( e ).toString() ); None
    }
  }

  // convert json to Map by binding key to corresponding LawMetadata: ultimately toMap converts our list of tuples into hash map
  lazy val laws: Map[String, LawMetadata] = keys.map( key ⇒ ( key, validateLaw( key ).get ) ).toMap

  override def law( key: String ): LawMetadata = laws( key )

  override def pamatlikums(): String = laws( Pamatlikums ).desc

  val versionsMap: HashMap[String, Seq[String]] = new HashMap()

  override def versions( key: String ) = {
    versionsMap.getOrElseUpdate( key, readVersions( key ) )
  }

  /** @return empty if error occurred reading file */
  def readVersions( key: String ): Seq[String] = {
    val versionFile = VersionsRoot+"/"+key+".ver"
    try {
      FileReader.readLines( versionFile )
    }
    catch {
      case e: Exception ⇒
        Logger.error( s"Failed to read $versionFile: ", e )
        Seq.empty
    }
  }
}
