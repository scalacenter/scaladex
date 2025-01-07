package scaladex.server.route

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import scaladex.core.model.Scala.*
import scaladex.core.test.Values.*
import scaladex.server.route.Badges.summaryOfLatestVersions

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.model.headers.Location
import org.apache.pekko.http.scaladsl.server.Route
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class BadgesTests extends ControllerBaseSuite with BeforeAndAfterAll:

  val badgesRoute: Route = new Badges(database).route

  override protected def beforeAll(): Unit =
    val f = Future.traverse(Cats.allArtifacts)(artifactService.insertArtifact(_, Seq.empty))
    Await.result(f, Duration.Inf)

  it("fallback to JVM artifacts") {
    Get(s"/${Cats.reference}/cats-core/latest-by-scala-version.svg") ~> badgesRoute ~> check {
      status shouldEqual StatusCodes.TemporaryRedirect
      val redirection = headers.collectFirst { case Location(uri) => uri }
      redirection should contain(
        Uri("https://img.shields.io/badge/cats--core_--_JVM-2.6.1_(Scala_3.x),_2.5.0_(Scala_2.13)-green.svg?")
      )
    }
  }

  it("fallback to sjs1 when targetType is js") {
    Get(s"/${Cats.reference}/cats-core/latest-by-scala-version.svg?targetType=js") ~> badgesRoute ~> check {
      status shouldEqual StatusCodes.TemporaryRedirect
      val redirection = headers.collectFirst { case Location(uri) => uri }
      redirection should contain(
        Uri("https://img.shields.io/badge/cats--core_--_Scala.js_1.x-2.6.1_(Scala_3.x)-green.svg?")
      )
    }
  }

  it("latest version for Scala.js 0.6") {
    Get(s"/${Cats.reference}/cats-core/latest-by-scala-version.svg?platform=sjs0.6") ~> badgesRoute ~> check {
      status shouldEqual StatusCodes.TemporaryRedirect
      val redirection = headers.collectFirst { case Location(uri) => uri }
      redirection should contain(
        Uri("https://img.shields.io/badge/cats--core_--_Scala.js_0.6-2.6.1_(Scala_2.13)-green.svg?")
      )
    }
  }

  it("latest version for Scala native 0.4") {
    Get(s"/${Cats.reference}/cats-core/latest-by-scala-version.svg?platform=native0.4") ~> badgesRoute ~> check {
      status shouldEqual StatusCodes.TemporaryRedirect
      val redirection = headers.collectFirst { case Location(uri) => uri }
      redirection should contain(
        Uri("https://img.shields.io/badge/cats--core_--_Scala_Native_0.4-2.6.1_(Scala_2.13)-green.svg?")
      )
    }
  }
end BadgesTests

class BadgesUnitTests extends AnyFunSpec with Matchers:
  it("should provide a concise summary of latest versions") {
    summaryOfLatestVersions(
      Map(
        `2.11` -> Seq(`7.0.0`, `7.1.0`),
        `2.12` -> Seq(`7.0.0`, `7.1.0`, `7.2.0`),
        `2.13` -> Seq(`7.0.0`, `7.1.0`, `7.2.0`, `7.3.0`),
        `3` -> Seq(`7.2.0`, `7.3.0`)
      )
    ) shouldBe "7.3.0 (Scala 3.x, 2.13), 7.2.0 (Scala 2.12), 7.1.0 (Scala 2.11)"
  }

  it("should prefer stable to pre-releases if both are available") {
    summaryOfLatestVersions(Map(`2.13` -> Seq(`7.0.0`, `7.1.0`, `7.2.0-PREVIEW.1`))) shouldBe "7.1.0 (Scala 2.13)"
  }

  it("should display latest pre-release if no full release is available") {
    summaryOfLatestVersions(
      Map(`2.13` -> Seq(`7.2.0-PREVIEW.1`, `7.2.0-PREVIEW.2`))
    ) shouldBe s"${`7.2.0-PREVIEW.2`} (Scala 2.13)"
  }
end BadgesUnitTests
