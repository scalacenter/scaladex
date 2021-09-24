package ch.epfl.scala.index.newModel

import ch.epfl.scala.index.model.misc.{GithubContributor, GithubInfo, GithubIssue, Url}

case class NewGithubInfo(
                          owner: String, // equivalent to Organization
                          name: String, // equivalent to repository
                          homepage: Option[Url],
                          description: Option[String],
                          logo: Option[Url],
                          stars: Option[Int],
                          forks: Option[Int],
                          watchers: Option[Int],
                          issues: Option[Int],
                          readme: Option[String] = None,
                          contributors: List[GithubContributor] = Nil,
                          commits: Option[Int] = None,
                          topics: Set[NewGithubInfo.Topic] = Set(),
                          beginnerIssues: List[GithubIssue] = Nil,
                          contributingGuide: Option[Url] = None,
                          codeOfConduct: Option[Url] = None,
                          chatroom: Option[Url] = None
                        ) {

  def toGithubInfo: GithubInfo = GithubInfo(
    name = name,
    owner = owner, homepage = homepage, description = description, logo = logo, stars = stars, forks = forks, watchers = watchers, issues = issues,
    readme = readme, contributors = contributors, contributorCount = contributors.size,
    commits = commits, topics = topics.map(_.value), contributingGuide = contributingGuide, codeOfConduct = codeOfConduct, chatroom = chatroom,
    beginnerIssuesLabel = None, beginnerIssues = Nil, selectedBeginnerIssues = Nil, filteredBeginnerIssues = Nil)

}
object NewGithubInfo {
  val empty: NewGithubInfo = ???
  case class Topic(value: String) extends AnyVal {
    override def toString: String = value
  }
  def from(g: GithubInfo): NewGithubInfo = NewGithubInfo(
    owner = g.owner, name = g.name, homepage = g.homepage, description = g.description, logo = g.logo, stars = g.stars, forks = g.forks, watchers = g.watchers, issues = g.issues, readme = g.readme, contributors = g.contributors, commits = g.commits,
    topics = g.topics.map(Topic), beginnerIssues = g.beginnerIssues, contributingGuide = g.contributingGuide, codeOfConduct = g.codeOfConduct, chatroom = g.chatroom)


}
