package scaladex.core.model

case class ProjectDependency(
    source: Project.Reference,
    sourceVersion: Version,
    target: Project.Reference,
    targetVersion: Version,
    scope: ArtifactDependency.Scope
)
