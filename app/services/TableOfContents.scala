package services

import scala.collection.immutable.Seq
import javax.inject.Singleton

import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import utils.FileReader
import utils.DateParam
import org.w3c.dom.Document

import scala.collection.mutable

/** component is injected into a controller */
trait TableOfContents {
    /** list of law keys available */
    def keys(): Seq[String]
    /** get full metadata */
    def law( key: String ): LawMetadata

    /** for testing */
    def pamatlikums(): String
}

object LikumiDb {
    /** actual content is provided by likumi-db project: https://github.com/valters/likumi-db */
    val AssetRoot: String = "app/likumi-db"
    /** main point of entry */
    val TocJsonAsset: String = AssetRoot+"/toc.json"
    val JsonRootKey: String = "likumi"
    val Pamatlikums: String = "satversme"
    /** for each law, known versions list is stored here, in .ver file */
    val VersionsRoot: String = AssetRoot+"/version"
    val DiffsRoot: String = AssetRoot+"/diff"
    val DiffReportSuffix: String = ".html.txt-diff.xml"
    /** we take content from this root node */
    val DiffElementXpath: String = "/diffreport/diff"
}

/** is read from json file directly */
case class LawMetadataJson( url: String, print_id: String, desc: String )

/** joins version information */
class LawMetadata( val key: String, val meta: LawMetadataJson, val versions: Seq[String] ) {
    /**
     * convert full euro date into abbreviated iso date, keep both together because we need them to print the version list.
     *  sample entry: ('01.02.2017', '20170201')
     */
    val isoVersions: Seq[( String, String )] = versions.map( s ⇒ ( s, DateParam.eurToIso( s ) ) )

    /** @param version iso date */
    def diffFileFor( version: String ): String = {
        LikumiDb.DiffsRoot+"/"+key+"/"+version + LikumiDb.DiffReportSuffix
    }

    def diffContent( file: String ): String = {
        if ( !FileReader.exists( file ) ) {
            s"(sorry, '$file' is not available)"
        }
        else {
            diffText( FileReader.readXml( file ) )
        }
    }

    /** take just the node text */
    def diffText( doc: Document ): String = {
      FileReader.nodeText( doc, LikumiDb.DiffElementXpath )
    }

}

/**
 * This class is a concrete implementation of the trait.
 */
@Singleton
class JsonTableOfContents extends TableOfContents {

    protected val json: JsValue = parseJson( LikumiDb.TocJsonAsset )

    /** generate the "implicit" converter for given case class */
    implicit val lawReads = Json.reads[LawMetadataJson]

    /** load json file into memory */
    def parseJson( assetFile: String ): JsValue = {
        val json: JsValue = FileReader.readJson( assetFile )
        ( json \ LikumiDb.JsonRootKey ).get
    }

    /** preserve original key order */
    lazy val keys: List[String] = json.as[JsObject].fields.map( t ⇒ t._1 ).toList

    /** in json lingo "validate" means marshal into native data structure - from json into scala */
    def validateLaw( key: String ): Option[LawMetadataJson] = {
        val jsResult = ( json \ key ).validate[LawMetadataJson]
        jsResult match {
            case s: JsSuccess[LawMetadataJson] ⇒ Some( s.get )
            case e: JsError ⇒ println( "Errors: "+JsError.toJson( e ).toString() ); None
        }
    }

    // convert json to Map by binding key to corresponding LawMetadata: ultimately toMap converts our list of tuples into hash map
    lazy val laws: Map[String, LawMetadataJson] = keys.map( key ⇒ ( key, validateLaw( key ).get ) ).toMap

    lazy val lawMap: mutable.HashMap[String, LawMetadata] = new mutable.HashMap()

    override def law( key: String ): LawMetadata = lawMap.getOrElseUpdate( key, new LawMetadata( key, laws( key ), versions( key ) ) )

    override def pamatlikums(): String = laws( LikumiDb.Pamatlikums ).desc

    /**
     * holds information we read from .ver files. A .ver file contains list of
     *  dates when law was changed, which we then later map to diff file
     *  (which is xml file containing changes report).
     */
    val versionsMap: mutable.HashMap[String, Seq[String]] = new mutable.HashMap()

    /**
     * @param key law short-id
     * @return list of known versions (dates when law was changed)
     */
    def versions( key: String ): Seq[String] = {
        versionsMap.getOrElseUpdate( key, readVersions( key ) )
    }

    /** @return empty if error occurred reading file */
    def readVersions( key: String ): Seq[String] = {
        val versionFile = LikumiDb.VersionsRoot+"/"+key+".ver"
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
