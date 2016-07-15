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
  * @param commits number of commits, calculated by contributors
  */
case class GithubInfo(
    readme: Option[String] = None,
    description: Option[String] = None,
    homepage: Option[Url] = None,
    logo: Option[Url] = None,
    stars: Option[Int] = None,
    forks: Option[Int] = None,
    watchers: Option[Int] = None,
    issues: Option[Int] = None,
    contributors: List[GithubContributor] = List(),
    commits: Option[Int] = None
)
