package ch.epfl.scala.index.server.routes

import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directives.{parameters, path, pathSingleSlash}
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.unmarshalling.Unmarshaller
import ch.epfl.scala.index.data.github.GithubCredentials
import ch.epfl.scala.index.server.UserState
import ch.megard.akka.http.cors.CorsDirectives.cors
import org.joda.time.DateTime

class Paths(userState: Directive1[Option[UserState]]) {

  def buildRoutes(behavior: HttpBehavior) = {
    val userFacingRoutes = concat(
      frontPagePath(behavior.frontPage),
      redirectToNoTrailingSlashIfPresent(akka.http.scaladsl.model.StatusCodes.MovedPermanently) {
        concat(
          editUpdatePath(behavior.updateProject),
          editPath(behavior.editProject),
          legacyArtifactQueryPath(behavior.projectPageArtifactQuery),
          projectPath(behavior.projectPage),
          artifactPath(behavior.artifactPage),
          artifactVersionPath(behavior.artifactPageWithVersion),
          searchPath(behavior.searchResultsPage),
          organizationPath(behavior.organizationPage)
        )
      }
    )

    val programmaticRoutes =
      concat(
        publishStatusPath(behavior.releaseStatus),
        publishUpdateRoute(behavior.credentialsTransformation)(behavior.publishRelease),
        apiSearchPath(behavior.projectSearchApi),
        apiProjectPath(behavior.releaseInfoApi),
        apiAutocompletePath(behavior.autocomplete),
        Assets.routes,
        versionBadgePath(behavior.versionBadge),
        queryBadgePath(behavior.countBadge),
        behavior.oAuth2routes
      )

    programmaticRoutes ~ userFacingRoutes
  }

  def frontPagePath = pathSingleSlash & userState

  def searchPath =
    get & path("search") & userState & parameters(('q, 'page.as[Int] ? 1, 'sort.?, 'you.?))

  val organizationPath = get & path(Segment)

  private val shieldsParameters = parameters(('color.?, 'style.?, 'logo.?, 'logoWidth.as[Int].?))

  val queryBadgePath = get & path("count.svg") & parameter('q) & shieldsParameters & parameters('subject)

  def versionBadgePath = get & path(Segment / Segment / Segment / "latest.svg") & shieldsParameters

  def editUpdatePath = post & path("edit" / Segment / Segment) & userState & pathEnd & formFieldSeq & formFields((
    'contributorsWanted.as[Boolean] ? false,
    'keywords.*,
    'defaultArtifact.?,
    'defaultStableVersion.as[Boolean] ? false,
    'deprecated.as[Boolean] ? false,
    'artifactDeprecations.*,
    'cliArtifacts.*,
    'customScalaDoc.?))

  def editPath = get & path("edit" / Segment / Segment) & userState & pathEnd

  def projectPath = get & path(Segment / Segment) & userState & pathEnd

  def legacyArtifactQueryPath = get & path(Segment / Segment) & parameters(('artifact, 'version.?))

  def artifactPath = get & path(Segment / Segment / Segment) & userState

  def artifactVersionPath = get & path(Segment / Segment / Segment / Segment) & userState

  private val DateTimeUn = Unmarshaller.strict[String, DateTime] { dateRaw =>
    new DateTime(dateRaw.toLong * 1000L)
  }

  private val publish = path("publish")

  private val publishParameters = parameters((
    'path,
    'created.as(DateTimeUn) ? DateTime.now,
    'readme.as[Boolean] ? true,
    'contributors.as[Boolean] ? true,
    'info.as[Boolean] ? true,
    'keywords.as[String].*,
    'test.as[Boolean] ? false
  ))

  def publishUpdateRoute(credentialsTransformation: (Option[HttpCredentials]) => Directive1[(GithubCredentials, UserState)]) = {
    put & publish & publishParameters & entity(as[String]) & extractCredentials.flatMap(credentialsTransformation)
  }

  val publishStatusPath = get & publish & parameter('path)

  private val apiPrefix = pathPrefix("api")

  val apiAutocompletePath = apiPrefix & path("autocomplete") & get & parameter('q)
  val apiProjectPath = apiPrefix & cors() & path("project") & get & parameters(('organization, 'repository, 'artifact.?))
  val apiSearchPath = apiPrefix & cors() & path("search") & get & parameters(('q, 'target, 'scalaVersion, 'scalaJsVersion.?, 'cli.as[Boolean] ? false))
}
