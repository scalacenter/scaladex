package scaladex.core.model.search

import scaladex.core.model.GithubIssue

/**
 * found project with issues hit by search engine
 */
final case class ProjectHit(
    document: ProjectDocument,
    beginnerIssueHits: Seq[GithubIssue]
) {
  def displayedIssues: Seq[GithubIssue] =
    if (beginnerIssueHits.nonEmpty) beginnerIssueHits
    else document.githubInfo.toSeq.flatMap(_.openIssues)
}
