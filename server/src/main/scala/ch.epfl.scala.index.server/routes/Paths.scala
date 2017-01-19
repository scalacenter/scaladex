package ch.epfl.scala.index.server.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directives.{parameters, path, pathSingleSlash}
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.{Directive1, Route}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import ch.epfl.scala.index.server.UserState
import ch.megard.akka.http.cors.CorsDirectives.cors
import org.joda.time.DateTime

class Paths(userState: Directive1[Option[UserState]]) {

  def apiRoutes(routes: Route*) = concat(routes: _*)

  def publishRoutes(routes: Route*) = concat(routes: _*)

  def badgeRoutes(routes: Route*) = concat(routes: _*)

  def projectRoutes(routes: Route*) = concat(routes: _*)

  def artifactRoutes(routes: Route*) = concat(routes: _*)

  def buildRoutes(behavior: HttpBehavior) = {
    val programmaticRoutes =
      concat(
        publishRoutes(
          (get & publish & parameter('path)) (behavior.releaseStatus),
          (put & publish & publishParameters & entity(as[String]) & extractCredentials.flatMap(behavior.credentialsTransformation)) (behavior.publishRelease)
        ),
        apiRoutes(
          (apiPrefix & cors() & path("search") & get & parameters(('q, 'target, 'scalaVersion, 'scalaJsVersion.?, 'cli.as[Boolean] ? false))) (behavior.projectSearchApi),
          (apiPrefix & cors() & path("project") & get & parameters(('organization, 'repository, 'artifact.?))) (behavior.releaseInfoApi),
          (apiPrefix & path("autocomplete") & get & parameter('q)) (behavior.autocomplete)
        ),
        Assets.routes,
        badgeRoutes(
          (get & path(Segment / Segment / Segment / "latest.svg") & shieldsParameters) (behavior.versionBadge),
          (get & path("count.svg") & parameter('q) & shieldsParameters & parameters('subject)) (behavior.countBadge)
        ),
        behavior.oAuth2routes
      )

    val userFacingRoutes =
      concat(
        (pathSingleSlash & userState) (behavior.frontPage),
        redirectToNoTrailingSlashIfPresent(akka.http.scaladsl.model.StatusCodes.MovedPermanently) {
          concat(
            projectRoutes(
              (post & path("edit" / Segment / Segment) & userState & pathEnd & formFieldSeq & editFormFields) (behavior.updateProject),
              (get & path("edit" / Segment / Segment) & userState & pathEnd) (behavior.editProject),
              (get & path(Segment / Segment) & parameters(('artifact, 'version.?))) (behavior.projectPageArtifactQuery),
              (get & path(Segment / Segment) & userState & pathEnd) (behavior.projectPage)
            ),
            artifactRoutes(
              (get & path(Segment / Segment / Segment) & userState) (behavior.artifactPage),
              (get & path(Segment / Segment / Segment / Segment) & userState) (behavior.artifactPageWithVersion)
            ),
            (get & path("search") & userState & parameters(('q, 'page.as[Int] ? 1, 'sort.?, 'you.?))) (behavior.searchResultsPage),
            (get & path(Segment) & userState) (behavior.organizationPage)
          )
        }
      )

    programmaticRoutes ~ userFacingRoutes
  }

  private val shieldsParameters = parameters(('color.?, 'style.?, 'logo.?, 'logoWidth.as[Int].?))

  private val editFormFields = formFields((
    'contributorsWanted.as[Boolean] ? false,
    'keywords.*,
    'defaultArtifact.?,
    'defaultStableVersion.as[Boolean] ? false,
    'deprecated.as[Boolean] ? false,
    'artifactDeprecations.*,
    'cliArtifacts.*,
    'customScalaDoc.?))

  private val DateTimeUn = Unmarshaller.strict[String, DateTime] { dateRaw =>
    new DateTime(dateRaw.toLong * 1000L)
  }

  private val publishParameters = parameters((
    'path,
    'created.as(DateTimeUn) ? DateTime.now,
    'readme.as[Boolean] ? true,
    'contributors.as[Boolean] ? true,
    'info.as[Boolean] ? true,
    'keywords.as[String].*,
    'test.as[Boolean] ? false
  ))

  private val publish = path("publish")

  private val apiPrefix = pathPrefix("api")
}
