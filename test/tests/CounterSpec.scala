package tests

import org.scalatestplus.play._
import services.Counter

/** Example of testing a Guice-injected component. */
class CounterSpec extends PlaySpec with OneAppPerSuite {

  "Counter object" should {
    "resolve its implementation and should produce increasing values" in {
      val counter: Counter = app.injector.instanceOf[Counter]
      counter.nextCount() mustBe 0
      counter.nextCount() mustBe 1
      counter.nextCount() mustBe 2
    }
  }
}
