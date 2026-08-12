package scaladex.loadtest

import io.gatling.core.Predef.*

/** Mixed ramp-to-failure: one request per virtual user, weighted to mirror real UI traffic, injected at a linearly
  * increasing rate to find the knee (where latency/errors blow up) and the first component to saturate.
  */
class MixedStressSimulation extends Simulation:

  private val mixed = scenario("Mixed load").randomSwitch(
    25.0 -> Chains.frontPage,
    25.0 -> Chains.search,
    20.0 -> Chains.projectPage,
    15.0 -> Chains.apiArtifact,
    10.0 -> Chains.autocomplete,
    5.0 -> Chains.awesome
  )

  setUp(mixed.inject(Stress.ramp))
    .protocols(ScaladexProtocol.httpProtocol)
    .assertions(global.successfulRequests.percent.gt(95.0))
end MixedStressSimulation
