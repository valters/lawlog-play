package tests

import org.scalatestplus.play._
import services.JsonTableOfContents

/** A very simple unit testing example. */
class JsonTocSpec extends PlaySpec {
  val appToc: JsonTableOfContents = new JsonTableOfContents

  "App TOC" should {
    "read and parse configuration" in {
      appToc.pamatlikums() mustBe "Latvijas Republikas Satversme"
    }

    "read law metadata" in {
      appToc.validateLaw("darba").get.desc mustBe "Darba likums"
    }

    "read keys" in {
      appToc.keys.size mustBe 29
    }

    "print structure" in {
      appToc.laws.values.map( _.desc ).size mustBe 29
    }

    "read an existing version data" in {
      appToc.versions("apl").size mustBe 7
    }

    "ignore missing version file" in {
      appToc.versions("$missing file$").size mustBe 0
    }

    "produce law version resource" in {
      appToc.law("apl").diffFileFor("-") mustBe "app/likumi-db/diff/apl/-.html.txt-diff.xml"
    }

    "print version list" in {
      for( ( date, isoDt ) <- appToc.law("apl").isoVersions ) {
        println( date +"+"+ isoDt )
      }
    }

    "extract diff from xml" in {
      appToc.law("apl").diffContent( "test/test.xml") mustBe "[IMG]"
    }

  }
}
