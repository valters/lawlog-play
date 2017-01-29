package utils

import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors

import scala.Vector
import scala.collection.convert.wrapAsScala.asScalaBuffer

import play.api.libs.json.JsValue
import play.api.libs.json.Json
import java.nio.charset.StandardCharsets
import io.github.valters.xml.XmlDomUtils
import org.w3c.dom.Document
import io.github.valters.xml.TransformToString
import io.github.valters.xml.XPathUtils

object FileReader {

  val transform: TransformToString = new TransformToString()
  val xpath: XPathUtils = new XPathUtils()

  /**
   * Uses java.nio API. Throws exception if anything goes wrong.
   *  @return scala collection
   */
  def readLines( file: String ): Vector[String] = {
    val stream = Files.lines( Paths.get( file ) )
    val lines: java.util.List[String] = try { stream.collect( Collectors.toList() ) } finally { stream.close() }
    Vector( lines: _* ) // operator _* explodes the list into var-args suitable for vector constructor
  }

  /** Uses java API. */
  def readJson( file: String ): JsValue = {
    val stream = new FileInputStream( new File( file ) )
    val json: JsValue = try { Json.parse( stream ) } finally { stream.close() }
    json
  }

  /** Uses java API. */
  def readFile( file: String ): String = {
    val bytes: Array[Byte] = Files.readAllBytes( Paths.get( file ) )
    new String( bytes, StandardCharsets.UTF_8 )
  }

  def readXml( file: String ): Document = {
    val stream = new java.io.FileInputStream( new File( file ) )
    val xml: Document = try { XmlDomUtils.documentBuilder().parse( stream ) } finally { stream.close() }
    xml
  }

  def nodeText( document: Document, xpathExpr: String ): String = {
    val text = transform.nodesToString( xpath.findNode( document, xpathExpr ).getChildNodes() )
    text
  }

  def exists( file: String ): Boolean = {
    new File( file ).exists()
  }

}
