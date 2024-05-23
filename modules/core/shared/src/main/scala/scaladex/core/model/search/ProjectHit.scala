package scaladex.core.model.search

import scaladex.core.model.GithubIssue

/**
 * found project with issues hit by search engine
 */
final case class ProjectHit(
    document: ProjectDocument,
    issues: Seq[GithubIssue]
) {
  def displayedIssues: Seq[GithubIssue] =
    if (issues.nonEmpty) issues
    else document.githubInfo.toSeq.flatMap(_.openIssues)
}
