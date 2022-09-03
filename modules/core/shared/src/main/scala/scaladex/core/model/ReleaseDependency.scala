package scaladex.core.model

import scaladex.core.model.ArtifactDependency.Scope

case class ReleaseDependency(
    source: Release,
    target: Release,
    scope: Scope
)
object ReleaseDependency {
  case class Direct(targetRef: Project.Reference, targetVersion: SemanticVersion, scope: Scope)
  case class Reverse(sourceRef: Project.Reference, targetVersion: SemanticVersion, scope: Scope)
}
