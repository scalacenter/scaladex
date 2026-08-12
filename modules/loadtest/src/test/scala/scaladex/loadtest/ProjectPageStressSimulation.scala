package scaladex.loadtest

import io.gatling.core.Predef.*

/** Isolated Postgres stress: only the SQL-backed endpoints (project page, version matrix, artifact API) ramped to
  * failure, to attribute saturation to the database and its joins specifically. Watch
  * `db_client_operation_duration_seconds` by `db_sql_table` in Grafana — especially `artifact_dependencies`.
  */
class ProjectPageStressSimulation extends Simulation:

  private val pgOnly = scenario("Project-page stress (Postgres)").randomSwitch(
    50.0 -> Chains.projectPage,
    30.0 -> Chains.versionMatrix,
    20.0 -> Chains.apiArtifact
  )

  setUp(pgOnly.inject(Stress.rampThenConstant))
    .protocols(ScaladexProtocol.httpProtocol)
    .assertions(global.successfulRequests.percent.gt(95.0))
end ProjectPageStressSimulation
