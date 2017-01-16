package ch.epfl.scala.index.server.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.server.Directives.authenticateBasicAsync
import akka.http.scaladsl.server.directives.AuthenticationDirective
import akka.stream.ActorMaterializer
import ch.epfl.scala.index.data.DataPaths
import ch.epfl.scala.index.data.github.GithubCredentials
import ch.epfl.scala.index.server._
import ch.epfl.scala.index.server.routes.api.{PublishApi, SearchApi}

import scala.concurrent.ExecutionContext

class ScaladexFacade(session: GithubUserSession,
                     frontPageSource: FrontPage,
                     projectPages: ProjectPages,
                     searchPages: SearchPages,
                     publishApi: PublishApi,
                     searchApi: SearchApi,
                     oAuth2: OAuth2,
                     badges: Badges,
                     override val credentialsTransformation: (Option[HttpCredentials]) => AuthenticationDirective[(GithubCredentials, UserState)]) extends HttpBehavior {

  override val frontPage = frontPageSource.frontPageBehavior _

  override val updateProject = projectPages.updateProjectBehavior _

  override val editProject = projectPages.getEditPageBehavior _

  override val projectPageArtifactQuery = projectPages.legacyArtifactQueryBehavior _

  override val projectPage = projectPages.projectPageBehavior _

  override val artifactPage = projectPages.artifactPageBehavior _

  override val artifactPageWithVersion = projectPages.artifactWithVersionBehavior _

  override val searchResultsPage = searchPages.searchPageBehavior _

  override val organizationPage = searchPages.organizationBehavior _

  override val releaseStatus = publishApi.publishStatusBehavior _

  override val publishRelease = publishApi.publishBehavior _

  override val projectSearchApi = searchApi.searchBehavior _

  override val releaseInfoApi = searchApi.projectBehavior _

  override val autocomplete = searchApi.autocompleteBehavior _

  override val versionBadge = badges.versionBadgeBehavior _

  override val countBadge = badges.countBadgeBehavior _

  override val oAuth2routes = oAuth2.routes
}

object ScaladexFacade {
  def createStandardFacade(data: DataRepository, session: GithubUserSession, github: Github, paths: DataPaths)
                          (implicit system: ActorSystem, materializer: ActorMaterializer, executionContext: ExecutionContext): HttpBehavior = {

    new ScaladexFacade(session,
      new FrontPage(data, session),
      new ProjectPages(data, session),
      new SearchPages(data, session),
      new PublishApi(paths, data, github),
      new SearchApi(data),
      new OAuth2(github, session),
      new Badges(data),
      (credentials: Option[HttpCredentials]) => {
        authenticateBasicAsync(realm = "Scaladex Realm", GithubAuthenticator(github, credentials)(executionContext))
      }
    )
  }
}