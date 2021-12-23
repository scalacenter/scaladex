package scaladex.core.model

import java.time.Instant

import scaladex.core.model.Project._
import scaladex.core.model.search.GithubInfoDocument

/**
 * Github Info to a project
 *
 * @param readme the html formatted readme file from repo
 * @param description the github description
 * @param homepage the github defined homepage ex: http://typelevel.org/cats/
 * @param logo the defined logo fo a project
 * @param stars the received stars
 * @param forks the number of forks
 * @param watchers number of subscribers to this repo
 * @param issues number of open issues for this repo
 * @param contributors list of contributor profiles
 * @param contributorCount how many contributors there are, used to sort search results by number of contributors
 * @param commits number of commits, calculated by contributors
 * @param topics topics associated with the project
 * @param contributingGuide CONTRIBUTING.md
 * @param codeOfConduct link to code of conduct
 * @param chatroom link to chatroom (ex: https://gitter.im/scalacenter/scaladex)
 * @param beginnerIssuesLabel label used to tag beginner-friendly issues
 * @param beginnerIssues list of beginner-friendly issues for the project
 * @param selectedBeginnerIssues list of beginner-friendly issues selected by maintainer which show in UI
 * @param filteredBeginnerIssues list of beginner-friendly issues that were filtered by contributing search
 */
case class GithubInfo(
    projectRef: Project.Reference,
    homepage: Option[Url],
    description: Option[String],
    logo: Option[Url],
    stars: Option[Int],
    forks: Option[Int],
    watchers: Option[Int],
    issues: Option[Int],
    creationDate: Option[Instant],
    readme: Option[String] = None,
    contributors: List[GithubContributor] = List(),
    commits: Option[Int] = None,
    topics: Set[String] = Set(),
    contributingGuide: Option[Url] = None,
    codeOfConduct: Option[Url] = None,
    chatroom: Option[Url] = None,
    beginnerIssues: List[GithubIssue] = List() // right now it's all issues, not only beginners issues
) {
  val organization: Organization = projectRef.organization
  val repository: Repository = projectRef.repository
  val contributorCount: Int = contributors.size

  def toDocument: GithubInfoDocument =
    GithubInfoDocument(
      avatarUrl = logo,
      description = description,
      readme = readme,
      beginnerIssues = beginnerIssues,
      topics = topics.toSeq,
      contributingGuide = contributingGuide,
      chatroom = chatroom,
      codeOfConduct = codeOfConduct,
      stars = stars,
      forks = forks,
      contributorCount = contributorCount
    )
}

object GithubInfo {
  def empty(repository: String, organization: String): GithubInfo = GithubInfo(
    Project.Reference.from(organization, repository),
    readme = None,
    homepage = None,
    description = None,
    logo = None,
    stars = None,
    forks = None,
    watchers = None,
    issues = None,
    creationDate = None,
    contributors = List(),
    commits = None,
    topics = Set(),
    contributingGuide = None,
    codeOfConduct = None,
    chatroom = None,
    beginnerIssues = List()
  )
}
