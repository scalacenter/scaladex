package scaladex.core.model

case class ProjectDependency(
    source: Project.Reference,
    target: Project.Reference
)
