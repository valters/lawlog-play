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
  }
}
