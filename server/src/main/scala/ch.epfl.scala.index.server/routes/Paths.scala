package ch.epfl.scala.index.server.routes

import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directives.{parameters, path, pathSingleSlash}
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.{Directive1}
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.unmarshalling.Unmarshaller
import ch.epfl.scala.index.data.github.GithubCredentials
import ch.epfl.scala.index.server.GithubUserSessionDirective.githubUser
import ch.epfl.scala.index.server.routes.api.{PublishApi, SearchApi}
import ch.epfl.scala.index.server.{Github, GithubUserSession, UserState}
import ch.megard.akka.http.cors.CorsDirectives.cors
import org.joda.time.DateTime

import scala.concurrent.{ExecutionContext, Future}

class Routes(session: GithubUserSession, frontPage: FrontPage, projectPages: ProjectPages, searchPages: SearchPages, publishApi: PublishApi, searchApi: SearchApi, oAuth2: OAuth2, badges: Badges) {

  val Paths = new Paths(githubUser(session))

  // Aggregation
  private def userFacingRoutes =
    concat(
      Paths.frontPagePath(frontPage.frontPageBehavior),
      redirectToNoTrailingSlashIfPresent(akka.http.scaladsl.model.StatusCodes.MovedPermanently) {
        concat(
          Paths.editUpdatePath(session)(projectPages.updateProjectBehavior),
          Paths.editPath(session)(projectPages.getEditPageBehavior),
          Paths.legacyArtifactQueryPath(session)(projectPages.legacyArtifactQueryBehavior),
          Paths.projectPath(session)(projectPages.projectPageBehavior),
          Paths.artifactPath(session)(projectPages.artifactPageBehavior),
          Paths.artifactVersionPath(session)(projectPages.artifactWithVersionBehavior),
          Paths.searchPath(session)(searchPages.searchPageBehavior),
          Paths.organizationPath(searchPages.organizationBehavior)
        )
      }
    )

  private val programmaticRoutes = concat(
    Paths.publishStatusPath(publishApi.publishStatusBehavior),
    Paths.publishUpdateRoute(publishApi.github)(publishApi.executionContext)(publishApi.publishBehavior),
    Paths.apiSearchPath(searchApi.searchBehavior),
    Paths.apiProjectPath(searchApi.projectBehavior),
    Paths.apiAutocompletePath(searchApi.autocompleteBehavior),
    Assets.routes,
    Paths.versionBadgePath(badges.versionBadgeBehavior),
    Paths.queryBadgePath(badges.countBadgeBehavior),
    oAuth2.routes
  )

  val routes = programmaticRoutes ~ userFacingRoutes
}


class Paths(userState: Directive1[Option[UserState]]) {

  def frontPagePath = pathSingleSlash & userState

  def searchPath(session: GithubUserSession) =
    get & path("search") & userState & parameters(('q, 'page.as[Int] ? 1, 'sort.?, 'you.?))

  val organizationPath = get & path(Segment)

  private val shieldsParameters = parameters(('color.?, 'style.?, 'logo.?, 'logoWidth.as[Int].?))

  val queryBadgePath = get & path("count.svg") & parameter('q) & shieldsParameters & parameters('subject)

  def versionBadgePath = get & path(Segment / Segment / Segment / "latest.svg") & shieldsParameters

  def editUpdatePath(session: GithubUserSession) = post & path("edit" / Segment / Segment) & userState & pathEnd & formFieldSeq & formFields((
    'contributorsWanted.as[Boolean] ? false,
    'keywords.*,
    'defaultArtifact.?,
    'defaultStableVersion.as[Boolean] ? false,
    'deprecated.as[Boolean] ? false,
    'artifactDeprecations.*,
    'cliArtifacts.*,
    'customScalaDoc.?))

  def editPath(session: GithubUserSession) = get & path("edit" / Segment / Segment) & userState & pathEnd

  def projectPath(session: GithubUserSession) = get & path(Segment / Segment) & userState & pathEnd

  def legacyArtifactQueryPath(session: GithubUserSession) = get & path(Segment / Segment) & parameters(('artifact, 'version.?))

  def artifactPath(session: GithubUserSession) = get & path(Segment / Segment / Segment) & userState

  def artifactVersionPath(session: GithubUserSession) = get & path(Segment / Segment / Segment / Segment) & userState

  /*
 * verifying a login to github
 * @param credentials the credentials
 * @return
 */
  private def githubAuthenticator(github: Github, credentialsHeader: Option[HttpCredentials])(implicit ec: ExecutionContext)
  : Credentials => Future[Option[(GithubCredentials, UserState)]] = {

    case Credentials.Provided(username) => {
      credentialsHeader match {
        case Some(cred) => {
          val upw = new String(new sun.misc.BASE64Decoder().decodeBuffer(cred.token()))
          val userPass = upw.split(":")

          val token = userPass(1)
          val credentials = GithubCredentials(token)
          // todo - catch errors

          github.getUserStateWithToken(token).map(user => Some((credentials, user)))
        }
        case _ => Future.successful(None)
      }
    }
    case _ => Future.successful(None)
  }

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

  def publishUpdateRoute(github: Github)(implicit ec: ExecutionContext) = put & publish & publishParameters & entity(as[String]) & extractCredentials.flatMap(credentials => authenticateBasicAsync(realm = "Scaladex Realm", githubAuthenticator(github, credentials)))

  val publishStatusPath = get & publish & parameter('path)

  private val apiPrefix = pathPrefix("api")

  val apiAutocompletePath = apiPrefix & path("autocomplete") & get & parameter('q)
  val apiProjectPath = apiPrefix & cors() & path("project") & get & parameters(('organization, 'repository, 'artifact.?))
  val apiSearchPath = apiPrefix & cors() & path("search") & get & parameters(('q, 'target, 'scalaVersion, 'scalaJsVersion.?, 'cli.as[Boolean] ? false))
}
