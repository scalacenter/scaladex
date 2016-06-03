package ch.epfl.scala.index
package data
package project

import model._

trait Delta
case class NewProject(project: Project) extends Delta
case class UpdatedProject(project: Project) extends Delta
case object NoOp extends Delta

object ProjectDelta {
  def apply(live: List[Project], update: List[Project]): List[Delta] = {
    // assume uniqueness for project reference and artifact reference
    def unique[K, V](map: Map[K, List[V]]): Map[K, V] = map.collect{case (k, h :: Nil) => (k, h)}
    def groupByProjectReference(ps: List[Project]) = unique(ps.groupBy(_.reference))
    def groupByArtifactReference(p: Project) = unique(p.artifacts.groupBy(_.reference))
    def groupByReleaseReference(a: Artifact) = unique(a.releases.groupBy(_.reference))
    
    def mergeArtifacts(liveArtifact: Artifact, updateArtifact: Artifact): Artifact = {
      val mergedReleases =
        fullOuterJoin(
          groupByReleaseReference(liveArtifact),
          groupByReleaseReference(updateArtifact)
        )((l, u) => l)(l => l)(u => u).values.toList

      liveArtifact.copy(
        releases = mergedReleases
      )
    }

    def mergeProjects(liveProject: Project, updateProject: Project): Delta = {
      val mergedArtifacts =
        fullOuterJoin(
          groupByArtifactReference(liveProject),
          groupByArtifactReference(updateProject)
        )((l, u) => mergeArtifacts(l, u))(l => l)(u => u).values.toList

      UpdatedProject(liveProject.copy(artifacts = mergedArtifacts, _id = updateProject._id))
    }

    fullOuterJoin(
      groupByProjectReference(live),
      groupByProjectReference(update)  
    )((l, u) => mergeProjects(l,u))(l => NoOp)(u => NewProject(u)).values.toList
  }
}