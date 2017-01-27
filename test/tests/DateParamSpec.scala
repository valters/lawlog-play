package tests

import org.scalatestplus.play._
import utils.DateParam

class DateParamSpec extends PlaySpec {

  "DateParam" should {

    "validate arg length" in {
      intercept[java.lang.IllegalArgumentException] {
        DateParam.isoToEur( "123" )
      }
    }

    "convert to euro style" in {
      DateParam.isoToEur( "20170201" ) mustBe "01.02.2017"
    }

    "convert to iso style" in {
      DateParam.eurToIso( "01.02.2017" ) mustBe "20170201"
    }

    "validate arg pattern as eur date" in {
      intercept[scala.MatchError] {
        DateParam.eurToIso( "01-02-2017" )
      }
      intercept[scala.MatchError] {
        DateParam.eurToIso( "2017.01.01" )
      }
    }

  }

}
