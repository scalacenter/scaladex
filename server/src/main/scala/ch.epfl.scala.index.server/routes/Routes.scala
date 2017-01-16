package ch.epfl.scala.index.server.routes

import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directives.{parameters, path, pathSingleSlash}
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.unmarshalling.Unmarshaller
import ch.epfl.scala.index.data.github.GithubCredentials
import ch.epfl.scala.index.server.GithubUserSessionDirective.githubUser
import ch.epfl.scala.index.server.{Github, GithubUserSession, UserState}
import org.joda.time.DateTime

import scala.concurrent.{ExecutionContext, Future}


object Routes {
  def frontPagePath = pathSingleSlash

  def searchPath(session: GithubUserSession) =
    path("search") & githubUser(session) & parameters(('q, 'page.as[Int] ? 1, 'sort.?, 'you.?))

  val organizationPath = path(Segment)

  private val shieldsParameters = parameters(('color.?, 'style.?, 'logo.?, 'logoWidth.as[Int].?))

  val queryBadgePath = path("count.svg") & parameter('q) & shieldsParameters & parameters('subject)

  def versionBadgePath = path(Segment / Segment / Segment / "latest.svg") & shieldsParameters

  def editUpdatePath(session: GithubUserSession) = post & path("edit" / Segment / Segment) & githubUser(session) & pathEnd & formFieldSeq & formFields((
    'contributorsWanted.as[Boolean] ? false,
    'keywords.*,
    'defaultArtifact.?,
    'defaultStableVersion.as[Boolean] ? false,
    'deprecated.as[Boolean] ? false,
    'artifactDeprecations.*,
    'cliArtifacts.*,
    'customScalaDoc.?))

  def editPath(session: GithubUserSession) = get & path("edit" / Segment / Segment) & githubUser(session) & pathEnd

  def projectPath(session: GithubUserSession) = path(Segment / Segment) & githubUser(session) & pathEnd

  def legacyArtifactQueryPath(session: GithubUserSession) = path(Segment / Segment) & parameters(('artifact, 'version.?))

  def artifactPath(session: GithubUserSession) = path(Segment / Segment / Segment) & githubUser(session)

  def artifactVersionPath(session: GithubUserSession) = path(Segment / Segment / Segment / Segment) & githubUser(session)

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

  val apiAutocompletePath = path("autocomplete") & get & parameter('q)
  val apiProjectPath = path("project") & get & parameters(('organization, 'repository, 'artifact.?))
  val apiSearchPath = path("search") & get & parameters(('q, 'target, 'scalaVersion, 'scalaJsVersion.?, 'cli.as[Boolean] ? false))
}
