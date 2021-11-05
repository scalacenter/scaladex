package ch.epfl.scala.index.model.misc

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
    name: String, // equivalent to repository
    owner: String, // equivalent to Organization
    homepage: Option[Url],
    description: Option[String],
    logo: Option[Url],
    stars: Option[Int],
    forks: Option[Int],
    watchers: Option[Int],
    issues: Option[Int],
    readme: Option[String] = None,
    contributors: List[GithubContributor] = List(),
    contributorCount: Int = 0,
    commits: Option[Int] = None,
    topics: Set[String] = Set(),
    contributingGuide: Option[Url] = None,
    codeOfConduct: Option[Url] = None,
    chatroom: Option[Url] = None,
    // added by the user in project page on scaladex
    beginnerIssuesLabel: Option[String] = None,
    beginnerIssues: List[GithubIssue] = List(),
    selectedBeginnerIssues: List[GithubIssue] = List(),
    filteredBeginnerIssues: List[GithubIssue] = List()
) {

  def displayIssues: List[GithubIssue] =
    if (filteredBeginnerIssues.nonEmpty) {
      filteredBeginnerIssues
    } else {
      val selectedIssueNumbers = selectedBeginnerIssues.map(_.number)
      val remainingIssues =
        beginnerIssues.filter(x => !selectedIssueNumbers.contains(x.number))
      selectedBeginnerIssues ++ remainingIssues
    }
}

object GithubInfo {
  val empty: GithubInfo = GithubInfo(
    name = "",
    owner = "",
    readme = None,
    description = None,
    homepage = None,
    logo = None,
    stars = None,
    forks = None,
    watchers = None,
    issues = None,
    contributors = List(),
    contributorCount = 0,
    commits = None,
    topics = Set(),
    contributingGuide = None,
    codeOfConduct = None,
    chatroom = None,
    beginnerIssuesLabel = None,
    beginnerIssues = List(),
    selectedBeginnerIssues = List(),
    filteredBeginnerIssues = List()
  )
}
