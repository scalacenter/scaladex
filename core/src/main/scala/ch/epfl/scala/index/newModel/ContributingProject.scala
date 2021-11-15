package ch.epfl.scala.index.newModel

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubIssue

// The contributing info of a project
case class ContributingProject(
    organization: NewProject.Organization,
    repository: NewProject.Repository,
    githubInfo: Option[GithubInfo],
    beginnerIssueHits: Seq[GithubIssue] // Hit by the search engine
) {
  def reference: NewProject.Reference = NewProject.Reference(organization, repository)

  def displayedIssues: Seq[GithubIssue] =
    // show issues hit by the search engine if non empty
    // otherwise show all issues starting by selected ones
    if (beginnerIssueHits.nonEmpty) beginnerIssueHits
    else {
      githubInfo.toSeq.flatMap { info =>
        val remainingIssues =
          info.beginnerIssues.filterNot(info.selectedBeginnerIssues.contains)
        info.selectedBeginnerIssues ++ remainingIssues
      }
    }
}
