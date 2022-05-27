package scaladex.infra.github

import java.time.Instant

import scala.util.Try

import cats.implicits.toTraverseOps
import io.circe._
import io.circe.generic.semiauto._
import scaladex.core.model
import scaladex.core.model.GithubContributor
import scaladex.core.model.GithubIssue
import scaladex.core.model.Project
import scaladex.core.model.Url
import scaladex.core.util.Secret

object GithubModel {

  def parseToInstant(s: String): Option[Instant] = Try(Instant.parse(s)).toOption
  case class Repository(
      name: String, // cat
      owner: String, // owner.login
      avatartUrl: String, // owner.avatar_url
      isPrivate: Boolean,
      description: Option[String],
      isFork: Boolean,
      createdAt: Option[String], // format: "2015-01-28T20:26:48Z",
      updatedAt: Option[String],
      homepage: Option[String], // http://typelevel.org/cats/
      stargazers_count: Int, // stars
      forks_count: Int,
      mirror_url: Option[String], // "mirror_url": "git://git.apache.org/spark.git",
      forks: Int,
      open_issues: Int,
      default_branch: String, // master
      subscribers_count: Int, // Watch
      topics: Seq[String],
      licenseName: Option[String]
  ) {

    def creationDate: Option[Instant] = createdAt.flatMap(parseToInstant)
    def ref: Project.Reference = Project.Reference.from(owner, name)
  }

  implicit val repositoryDecoder: Decoder[Repository] = new Decoder[Repository] {
    final def apply(cursor: HCursor): Decoder.Result[Repository] = {
      val c = cursor.withFocus(json => json.dropNullValues)
      for {
        name <- c.downField("name").as[String]
        owner <- c.downField("owner").downField("login").as[String]
        avatartUrl <- c.downField("owner").downField("avatar_url").as[String]
        isPrivate <- c.downField("private").as[Boolean]
        description <- c.downField("description").as[Option[String]]
        isFork <- c.downField("fork").as[Boolean]
        createdAt <- c.downField("created_at").as[Option[String]]
        updatedAt <- c.downField("updated_at").as[Option[String]]
        homepage <- c.downField("homepage").as[Option[String]]
        stars <- c.downField("stargazers_count").as[Int]
        forks_count <- c.downField("forks_count").as[Int]
        mirror_url <- c.downField("mirror_url").as[Option[String]]
        forks <- c.downField("forks").as[Int]
        open_issues <- c.downField("open_issues").as[Int]
        default_branch <- c.downField("default_branch").as[String]
        subscribers_count <- c.downField("subscribers_count").as[Int]
        topics <- c.downField("topics").as[Seq[String]]
        license <- c.downField("license").downField("spdx_id").as[Option[String]]
      } yield Repository(
        name.toLowerCase,
        owner.toLowerCase,
        avatartUrl,
        isPrivate,
        description,
        isFork,
        createdAt,
        updatedAt,
        homepage,
        stars,
        forks_count,
        mirror_url,
        forks,
        open_issues,
        default_branch,
        subscribers_count,
        topics,
        license
      )
    }
  }

  case class Topic(value: String) extends AnyVal

  implicit val TopicDecoder: Decoder[List[Topic]] = new Decoder[List[Topic]] {
    final def apply(c: HCursor): Decoder.Result[List[Topic]] =
      for {
        value <- c.downField("names").as[List[String]]
      } yield value.map(Topic)
  }

  case class Contributor(
      login: String,
      avatar_url: String,
      html_url: String,
      contributions: Int
  ) {
    def toGithubContributor: GithubContributor =
      GithubContributor(login, avatar_url, Url(html_url), contributions)

  }

  implicit val ContributorDecoder: Decoder[Contributor] = deriveDecoder

  case class CommunityProfile(
      contributingFile: Option[String],
      codeOfConductFile: Option[String],
      licenceFile: Option[String]
  )

  implicit val communityProfileDecoder: Decoder[CommunityProfile] = new Decoder[CommunityProfile] {
    final def apply(c: HCursor): Decoder.Result[CommunityProfile] =
      for {
        contributingFile <- c.downField("files").downField("contributing").downField("html_url").as[Option[String]]
        codeOfConductFile <- c.downField("files").downField("code_of_conduct").downField("html_url").as[Option[String]]
        licenceFile <- c.downField("files").downField("license").downField("html_url").as[Option[String]]
      } yield CommunityProfile(contributingFile, codeOfConductFile, licenceFile)
  }

  case class OpenIssue(
      number: Int,
      title: String,
      url: String,
      labels: Seq[String]
  ) {
    def toGithubIssue: GithubIssue =
      GithubIssue(number = number, title = title, url = Url(url))
  }

  implicit val openIssueDecoder: Decoder[Option[OpenIssue]] = new Decoder[Option[OpenIssue]] {
    final def apply(c: HCursor): Decoder.Result[Option[OpenIssue]] =
      for {
        isPullrequest <- c.downField("pull_request").as[Option[Json]]
        number <- c.downField("number").as[Int]
        url <- c.downField("html_url").as[String]
        title <- c.downField("title").as[String]
        labelsJson <- c.downField("labels").as[Seq[Json]]
        labelNames <- labelsJson.traverse(_.hcursor.downField("name").as[String])
      } yield if (isPullrequest.isEmpty) Some(OpenIssue(number, title, url, labelNames)) else None
  }

  case class GraphQLPage[T](endCursor: Option[String], hasNextPage: Boolean, nodes: Seq[T])
  case class RepoWithPermission(nameWithOwner: String, viewerPermission: String)

  implicit val repoWithPermissionDecoder: Decoder[RepoWithPermission] = deriveDecoder

  def graphqlPageDecoder[T: Decoder](downFields: String*): Decoder[GraphQLPage[T]] =
    (c: HCursor) => {
      val cursor = downFields.foldLeft[ACursor](c)(_.downField(_))
      for {
        endCursor <- cursor.downField("pageInfo").downField("endCursor").as[Option[String]]
        hasNextPage <- cursor.downField("pageInfo").downField("hasNextPage").as[Boolean]
        nodes <- cursor.downField("nodes").as[Seq[T]]
      } yield GraphQLPage(endCursor, hasNextPage, nodes)
    }

  case class UserInfo(login: String, name: Option[String], avatarUrl: String) {
    def toCoreUserInfo(token: Secret): model.UserInfo =
      model.UserInfo(login, name, avatarUrl, token)

  }
  val userInfoCaseClassDecoder: Decoder[UserInfo] = deriveDecoder
  implicit val userInfoDecoder: Decoder[UserInfo] =
    (c: HCursor) => c.downField("data").downField("viewer").as[UserInfo](userInfoCaseClassDecoder)
}
