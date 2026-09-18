package scaladex.infra

import scala.concurrent.duration.*

import org.apache.pekko.pattern.CircuitBreakerOpenException
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ResilienceTests extends AnyFunSpec with Matchers:
  private val recover = Resilience.tolerateHttpClientErrors(0)

  describe("Resilience.tolerateHttpClientErrors") {
    it("recovers non-fatal failures to the fallback value") {
      val error = new RuntimeException("boom")
      recover.isDefinedAt(error) shouldBe true
      recover(error) shouldBe 0
    }

    it("leaves an open circuit breaker unhandled so it propagates and aborts the batch") {
      recover.isDefinedAt(new CircuitBreakerOpenException(1.second)) shouldBe false
    }
  }

  describe("Resilience.tolerateHttpClientErrorsWith") {
    it("derives the fallback value from the error") {
      val recoverWith = Resilience.tolerateHttpClientErrorsWith[String](_.getMessage)
      recoverWith(new RuntimeException("boom")) shouldBe "boom"
      recoverWith.isDefinedAt(new CircuitBreakerOpenException(1.second)) shouldBe false
    }
  }
end ResilienceTests
