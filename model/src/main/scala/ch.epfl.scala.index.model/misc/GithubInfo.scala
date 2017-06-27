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
  * @param beginnerIssuesLabel label used to tag beginner-friendly issues
  * @param beginnerIssues list of beginner-friendly issues for the project
  */
case class GithubInfo(
    name: String = "",
    owner: String = "",
    readme: Option[String] = None,
    description: Option[String] = None,
    homepage: Option[Url] = None,
    logo: Option[Url] = None,
    stars: Option[Int] = None,
    forks: Option[Int] = None,
    watchers: Option[Int] = None,
    issues: Option[Int] = None,
    contributors: List[GithubContributor] = List(),
    contributorCount: Int = 0,
    commits: Option[Int] = None,
    topics: Set[String] = Set(),
    contributingGuide: Option[Url] = None,
    beginnerIssuesLabel: Option[String] = None,
    beginnerIssues: List[GithubIssue] = List()
)
