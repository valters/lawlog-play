package utils

import java.nio.file.Paths
import java.nio.file.Files
import java.util.stream.Collectors
import scala.collection.convert.wrapAsScala._
import scala.Vector

object FileReader {

  /** Uses java.nio API. Throws exception if anything goes wrong.
   *  @return scala collection
   */
  def readLines( file: String ): Vector[String] = {
    val stream = Files.lines( Paths.get( file ) )
    val lines: java.util.List[String] = try { stream.collect( Collectors.toList() ) } finally { stream.close() }
    Vector(lines: _*) // operator _* explodes the list into var-args suitable for vector constructor
  }

}
