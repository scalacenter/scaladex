package scaladex.core.model

import java.time.Instant

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
 * @param openIssues list of all open issues for the project
 */
case class GithubInfo(
    homepage: Option[Url],
    description: Option[String],
    logo: Option[Url],
    stars: Option[Int],
    forks: Option[Int],
    watchers: Option[Int],
    issues: Option[Int],
    creationDate: Option[Instant],
    readme: Option[String],
    contributors: List[GithubContributor],
    commits: Option[Int],
    topics: Set[String],
    contributingGuide: Option[Url],
    codeOfConduct: Option[Url],
    chatroom: Option[Url],
    openIssues: List[GithubIssue] // right now it's all issues, not only beginners issues
) {
  val contributorCount: Int = contributors.size

  def toDocument: GithubInfoDocument =
    GithubInfoDocument(
      logo = logo,
      description = description,
      readme = readme,
      openIssues = openIssues,
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
  val empty: GithubInfo = GithubInfo(
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
    openIssues = List()
  )
}
