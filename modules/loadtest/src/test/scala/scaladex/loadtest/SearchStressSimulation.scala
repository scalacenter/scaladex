package scaladex.loadtest

import io.gatling.core.Predef.*

/** Isolated Elasticsearch stress: only the ES-backed endpoints (search, autocomplete, front page) ramped to failure, to
  * attribute saturation to the search engine specifically. Watch `search_request_duration_seconds` in Grafana.
  */
class SearchStressSimulation extends Simulation:

  private val searchOnly = scenario("Search stress (Elasticsearch)").randomSwitch(
    50.0 -> Chains.search,
    30.0 -> Chains.autocomplete,
    20.0 -> Chains.frontPage
  )

  setUp(searchOnly.inject(Stress.rampThenConstant))
    .protocols(ScaladexProtocol.httpProtocol)
    .assertions(global.successfulRequests.percent.gt(95.0))
end SearchStressSimulation
