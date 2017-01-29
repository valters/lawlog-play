package tests

import org.scalatestplus.play._
import utils.FileReader
import scala.xml.Elem

class FileReaderSpec extends PlaySpec {

  "FileReader" should {

    "read all lines in file" in {
      val lines = FileReader.readLines( "test/test.ver" )
      lines(0) mustBe "0"
      lines(1) mustBe "1"
      lines(5) mustBe "5"
    }

    "read all content in file" in {
      val line = FileReader.readFile( "test/test.ver" )
      line mustBe "0\n1\n2\n3\n4\n5\n6"
    }

    "read xml" in {
      val txt: String = FileReader.nodeText( FileReader.readXml( "test/test.xml" ), "/diffreport/diff" )
      txt mustBe "[IMG]<p>test</p>\n<br/>\n[GIF]"
    }

  }

}
