package ch.epfl.scala.search

import ch.epfl.scala.index.model.misc.GithubIssue
import ch.epfl.scala.index.newModel.ContributingProject

/**
  * found project with issues hit by search engine
  */
final case class ProjectHit(
    document: ProjectDocument,
    beginnerIssueHits: Seq[GithubIssue]
) {
  def contributingInfo: ContributingProject = ContributingProject(
    document.organization,
    document.repository,
    document.githubInfo,
    beginnerIssueHits
  )
}
