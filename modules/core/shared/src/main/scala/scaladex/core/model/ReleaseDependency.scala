package scaladex.core.model

import java.time.Instant

import scaladex.core.model.ArtifactDependency.Scope

case class ReleaseDependency(
    source: Release,
    target: Release,
    scope: Scope
)
object ReleaseDependency {
  case class Result(ref: Project.Reference, version: SemanticVersion, scope: Scope, releaseDate: Instant)
}
