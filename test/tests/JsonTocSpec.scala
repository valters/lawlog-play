package tests

import org.scalatestplus.play._
import services.JsonTableOfContents

/** A very simple unit testing example. */
class JsonTocSpec extends PlaySpec {
  val json: JsonTableOfContents = new JsonTableOfContents

  "Json TOC" should {
    "read and parse configuration" in {
      json.likums() mustBe "Latvijas Republikas Satversme"
    }

    "read law metadata" in {
      json.law("darba").get.desc mustBe "Darba likums"
    }

    "read keys" in {
      json.keys.size mustBe 29
    }

    "print structure" in {
      println("oh:"+json.laws.map( entry => entry._2.desc ) )
    }
  }
}
