package ch.epfl.scala.search

import ch.epfl.scala.index.model.misc.GithubIssue
import ch.epfl.scala.index.model.misc.Url

case class GithubInfoDocument(
    avatarUrl: Option[Url],
    description: Option[String],
    readme: Option[String],
    beginnerIssues: List[GithubIssue],
    topics: Seq[String],
    contributingGuide: Option[Url],
    chatroom: Option[Url],
    codeOfConduct: Option[Url],
    stars: Option[Int],
    forks: Option[Int],
    contributorCount: Int
)
