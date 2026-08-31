package scaladex.infra

import scala.util.Failure
import scala.util.Success

import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCode
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class GithubClientImplUnitTests extends AnyFunSpec with Matchers:
  private def response(status: StatusCode, headers: (String, String)*) =
    HttpResponse(status = status, headers = headers.map((k, v) => RawHeader(k, v)).toList)

  describe("isBreakerFailure") {
    it("trips on a rate-limited 403 (X-RateLimit-Remaining: 0)") {
      GithubClientImpl.isBreakerFailure(Success(response(StatusCodes.Forbidden, "X-RateLimit-Remaining" -> "0"))) shouldBe true
    }
    it("trips on a 403 carrying a Retry-After header") {
      GithubClientImpl.isBreakerFailure(Success(response(StatusCodes.Forbidden, "Retry-After" -> "60"))) shouldBe true
    }
    it("does not trip on a plain 403 (e.g. contributor list too large)") {
      GithubClientImpl.isBreakerFailure(Success(response(StatusCodes.Forbidden))) shouldBe false
    }
    it("trips on 429") {
      GithubClientImpl.isBreakerFailure(Success(response(StatusCodes.TooManyRequests))) shouldBe true
    }
    it("trips on 5xx") {
      GithubClientImpl.isBreakerFailure(Success(response(StatusCodes.BadGateway))) shouldBe true
    }
    it("does not trip on 404") {
      GithubClientImpl.isBreakerFailure(Success(response(StatusCodes.NotFound))) shouldBe false
    }
    it("does not trip on 200") {
      GithubClientImpl.isBreakerFailure(Success(response(StatusCodes.OK))) shouldBe false
    }
    it("trips on a failed request (timeout/connection error)") {
      GithubClientImpl.isBreakerFailure(Failure(new RuntimeException("boom"))) shouldBe true
    }
  }
end GithubClientImplUnitTests
