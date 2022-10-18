package scaladex.core.model

case class ProjectDependency(
    source: Project.Reference,
    sourceVersion: SemanticVersion,
    target: Project.Reference,
    targetVersion: SemanticVersion,
    scope: ArtifactDependency.Scope
)
