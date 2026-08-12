package scaladex.loadtest

import io.gatling.core.Predef.*
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef.*

/** Reusable request chains, one per user-facing endpoint family, annotated with the backend each one exercises. Each
  * chain self-feeds so it can be composed freely.
  */
object Chains:

  val frontPage: ChainBuilder = // `/` fans out to ~6 ES queries
    exec(http("Front page").get("/").check(status.is(200)))

  val search: ChainBuilder =
    feed(Feeders.searchTerms)
      .exec(http("Search").get("/search?q=#{term}&sort=Stars&page=1").check(status.is(200)))

  val autocomplete: ChainBuilder =
    feed(Feeders.searchTerms)
      .exec(http("Autocomplete").get("/api/autocomplete?q=#{term}").check(status.is(200)))

  val awesome: ChainBuilder = // `/awesome` fans out to ~3 ES queries
    exec(http("Awesome").get("/awesome").check(status.is(200)))

  val projectPage: ChainBuilder = // project -> header -> dependencies -> dependents
    feed(Feeders.projects)
      .exec(http("Project page").get("/#{organization}/#{repository}").check(status.is(200)))

  val projectArtifacts: ChainBuilder =
    feed(Feeders.projects)
      .exec(http("Project artifacts").get("/#{organization}/#{repository}/artifacts").check(status.is(200)))

  val versionMatrix: ChainBuilder = // getArtifactRefs — large scan
    feed(Feeders.projects)
      .exec(http("Version matrix").get("/#{organization}/#{repository}/version-matrix").check(status.is(200)))

  val apiArtifact: ChainBuilder =
    feed(Feeders.artifacts)
      .exec(
        http("API artifact")
          .get("/api/v1/artifacts/#{groupId}/#{artifactId}/#{version}")
          .check(status.is(200))
      )
end Chains
