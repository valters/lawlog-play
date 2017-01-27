package tests

import org.scalatestplus.play._
import play.api.test._
import play.api.test.Helpers._

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
class ApplicationSpec extends PlaySpec with OneAppPerTest {

  "Routes" should {

    "send 404 on a bad request" in  {
      route(app, FakeRequest(GET, "/boum")).map(status(_)) mustBe Some(NOT_FOUND)
    }

  }

  "HomeController" should {

    "render the index page" in {
      val home = route(app, FakeRequest(GET, "/")).get

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Your new application is ready.")
    }

  }

  "LawController" should {

    "render the law page" in {
      val home = route(app, FakeRequest(GET, "/likums/satversme")).get

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("satversme")
    }

    "render 404 for non-existing law" in {
      val home = route(app, FakeRequest(GET, "/likums/$non-existing$")).get

      status(home) mustBe NOT_FOUND
      contentAsString(home) must include ("$non-existing$")
    }

  }

}
