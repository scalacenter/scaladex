package scaladex.core.model

case class ProjectDependency(
    source: Project.Reference,
    sourceVersion: Version,
    target: Project.Reference,
    targetVersion: Version,
    scope: ArtifactDependency.Scope
)

object ProjectDependency:
  /** Collapses rows to one per project on the given side, keeping the lowest scope and the highest versions. */
  def collapseByProject(
      rows: Seq[ProjectDependency],
      side: ProjectDependency => Project.Reference
  ): Seq[ProjectDependency] =
    rows
      .groupBy(side)
      .values
      .map { group =>
        group
          .minBy(_.scope)
          .copy(
            sourceVersion = group.map(_.sourceVersion).max,
            targetVersion = group.map(_.targetVersion).max
          )
      }
      .toSeq
      .sortBy(dependency => (side(dependency).organization.value, side(dependency).repository.value))
end ProjectDependency
