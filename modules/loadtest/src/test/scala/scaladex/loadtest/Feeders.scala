package scaladex.loadtest

import io.gatling.core.Predef.*
import io.gatling.core.feeder.FileBasedFeederBuilder

/** CSV feeders generated from `small-index` by the `data` module's `GenerateFeeders`.
  *
  * Regenerate them with: {{{sbt "data/run generateFeeders"}}}
  */
object Feeders:
  // organization,repository
  val projects: FileBasedFeederBuilder[String] = csv("data/orgs_repos.csv").random
  // organization,repository,groupId,artifactId,version
  val artifacts: FileBasedFeederBuilder[String] = csv("data/artifacts.csv").random
  // term
  val searchTerms: FileBasedFeederBuilder[String] = csv("data/search_terms.csv").random
end Feeders
