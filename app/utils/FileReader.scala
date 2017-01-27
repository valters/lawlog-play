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

object FileReader {

  /** Uses java.nio API. Throws exception if anything goes wrong.
   *  @return scala collection
   */
  def readLines( file: String ): Vector[String] = {
    val stream = Files.lines( Paths.get( file ) )
    val lines: java.util.List[String] = try { stream.collect( Collectors.toList() ) } finally { stream.close() }
    Vector(lines: _*) // operator _* explodes the list into var-args suitable for vector constructor
  }

  /** Uses java API. */
  def readJson( file: String): JsValue = {
    val stream = new FileInputStream( new File( file ) )
    val json: JsValue = try { Json.parse( stream ) } finally { stream.close() }
    json
  }

}
