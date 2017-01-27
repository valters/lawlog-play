package services

import scala.collection.mutable.HashMap

import javax.inject.Singleton
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import utils.FileReader

/** component is injected into a controller */
trait TableOfContents {
  /** list of law keys available */
  def keys(): Seq[String]
  /** get full metadata */
  def law( key: String ): LawMetadata
  /** @param key law short-id
   * @return list of known versions (dates when law was changed)
   */
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
  /** actual content is provided by likumi-db project: https://github.com/valters/likumi-db */
  protected val AssetRoot = "app/likumi-db"
  /** main point of entry */
  protected val TocJsonAsset = AssetRoot+"/toc.json"
  protected val JsonRootKey = "likumi"
  protected val Pamatlikums = "satversme"
  /** for each law, known versions list is stored here, in .ver file */
  protected val VersionsRoot = AssetRoot+"/version"

  protected val json: JsValue = readJson()

  implicit val lawReads = Json.reads[LawMetadata]

  def readJson(): JsValue = {
    val json: JsValue = FileReader.readJson( TocJsonAsset )
    ( json \ JsonRootKey ).get
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

  /** holds information we read from .ver files. A .ver file contains list of
   *  dates when law was changed, which we then later map to diff file
   *  (which is xml file containing changes report).
   */
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
