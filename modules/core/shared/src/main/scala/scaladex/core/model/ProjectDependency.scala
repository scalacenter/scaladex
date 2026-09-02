package scaladex.core.model

case class ProjectDependency(
    source: Project.Reference,
    sourceVersion: Version,
    target: Project.Reference,
    targetVersion: Version,
    scope: ArtifactDependency.Scope
)

object ProjectDependency:
  /** Reduces raw dependency rows to a single representative row per project on the given side (the depended-on project
    * for direct dependencies, the dependent project for reverse ones), keeping the lowest scope and the highest source
    * and target versions. This keeps a paginated list aligned with the distinct-project counts.
    */
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
