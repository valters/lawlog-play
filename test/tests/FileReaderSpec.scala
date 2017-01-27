package tests

import org.scalatestplus.play._
import utils.FileReader

class FileReaderSpec extends PlaySpec {

  "FileReader" should {

    "read all lines in file" in {
      val lines = FileReader.readLines( "test/test.ver" )
      lines(0) mustBe "0"
      lines(1) mustBe "1"
      lines(5) mustBe "5"
    }
  }

}
