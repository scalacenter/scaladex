package scaladex.core.model.search

import scaladex.core.model.GithubIssue
import scaladex.core.model.License
import scaladex.core.model.Url

case class GithubInfoDocument(
    logo: Option[Url],
    description: Option[String],
    readme: Option[String],
    openIssues: Seq[GithubIssue],
    topics: Seq[String],
    contributingGuide: Option[Url],
    codeOfConduct: Option[Url],
    stars: Option[Int],
    forks: Option[Int],
    contributorCount: Int,
    scalaPercentage: Option[Int],
    license: Option[License],
    commitsPerYear: Option[Int]
)

object GithubInfoDocument {
  def empty: GithubInfoDocument =
    GithubInfoDocument(None, None, None, Seq.empty, Seq.empty, None, None, None, None, 0, None, None, None)
}
