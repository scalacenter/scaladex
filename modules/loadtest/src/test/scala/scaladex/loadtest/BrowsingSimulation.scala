package scaladex.loadtest

import scala.concurrent.duration.DurationInt

import scaladex.loadtest.Stress.constantDuration
import scaladex.loadtest.Stress.rampDuration

import io.gatling.core.Predef.*

/** Sustained, realistic browsing load: each virtual user runs a short session (front page → search → autocomplete →
  * project → artifacts → one of api/version-matrix/awesome) with think-time pauses.
  */
class BrowsingSimulation extends Simulation:

  private val userRate = 10

  private val browse = scenario("Browsing session")
    .exec(Chains.frontPage)
    .pause(1, 3)
    .exec(Chains.search)
    .pause(1, 4)
    .exec(Chains.autocomplete)
    .pause(1, 3)
    .exec(Chains.projectPage)
    .pause(1, 4)
    .exec(Chains.projectArtifacts)
    .pause(1, 3)
    .randomSwitch(
      50.0 -> Chains.apiArtifact,
      30.0 -> Chains.versionMatrix,
      20.0 -> Chains.awesome
    )

  setUp(
    browse.inject(
      rampUsersPerSec(1).to(userRate).during(rampDuration.seconds),
      constantUsersPerSec(userRate).during(constantDuration.seconds)
    )
  )
    .protocols(ScaladexProtocol.httpProtocol)
    .assertions(
      global.successfulRequests.percent.gt(99.0),
      global.responseTime.percentile(95.0).lt(1000)
    )
end BrowsingSimulation
