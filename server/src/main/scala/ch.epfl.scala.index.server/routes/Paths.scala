package ch.epfl.scala.index.server.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directives.{parameters, path, pathSingleSlash}
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.ActorMaterializer
import ch.epfl.scala.index.data.DataPaths
import ch.epfl.scala.index.data.github.GithubCredentials
import ch.epfl.scala.index.server.GithubUserSessionDirective.githubUser
import ch.epfl.scala.index.server.{DataRepository, Github, GithubUserSession, UserState}
import ch.megard.akka.http.cors.CorsDirectives.cors
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext

class Routes(data: DataRepository, session: GithubUserSession, github: Github, paths: DataPaths)
            (implicit system: ActorSystem, materializer: ActorMaterializer, executionContext: ExecutionContext) {

  private val facade = ScaladexFacade.createStandardFacade(data, session, github, paths)

  val routes = new Paths(githubUser(session)).buildRoutes(facade)
}


class Paths(userState: Directive1[Option[UserState]]) {

  def buildRoutes(facade: HttpBehavior) = {
    val userFacingRoutes = concat(
      frontPagePath(facade.frontPageBehavior),
      redirectToNoTrailingSlashIfPresent(akka.http.scaladsl.model.StatusCodes.MovedPermanently) {
        concat(
          editUpdatePath(facade.updateProjectBehavior),
          editPath(facade.getEditPageBehavior),
          legacyArtifactQueryPath(facade.legacyArtifactQueryBehavior),
          projectPath(facade.projectPageBehavior),
          artifactPath(facade.artifactPageBehavior),
          artifactVersionPath(facade.artifactWithVersionBehavior),
          searchPath(facade.searchPageBehavior),
          organizationPath(facade.organizationBehavior)
        )
      }
    )

    val programmaticRoutes =
      concat(
        publishStatusPath(facade.publishStatusBehavior),
        publishUpdateRoute(facade.credentialsTransformation)(facade.publishBehavior),
        apiSearchPath(facade.searchBehavior),
        apiProjectPath(facade.projectBehavior),
        apiAutocompletePath(facade.autocompleteBehavior),
        Assets.routes,
        versionBadgePath(facade.versionBadgeBehavior),
        queryBadgePath(facade.countBadgeBehavior),
        facade.oAuth2routes
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
