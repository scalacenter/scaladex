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
                     frontPage: FrontPage,
                     projectPages: ProjectPages,
                     searchPages: SearchPages,
                     publishApi: PublishApi,
                     searchApi: SearchApi,
                     oAuth2: OAuth2,
                     badges: Badges,
                     override val credentialsTransformation: (Option[HttpCredentials]) => AuthenticationDirective[(GithubCredentials, UserState)]) extends HttpBehavior {

  override val frontPageBehavior = frontPage.frontPageBehavior _

  override val updateProjectBehavior = projectPages.updateProjectBehavior _

  override val getEditPageBehavior = projectPages.getEditPageBehavior _

  override val legacyArtifactQueryBehavior = projectPages.legacyArtifactQueryBehavior _

  override val projectPageBehavior = projectPages.projectPageBehavior _

  override val artifactPageBehavior = projectPages.artifactPageBehavior _

  override val artifactWithVersionBehavior = projectPages.artifactWithVersionBehavior _

  override val searchPageBehavior = searchPages.searchPageBehavior _

  override val organizationBehavior = searchPages.organizationBehavior _

  override val publishStatusBehavior = publishApi.publishStatusBehavior _

  override val publishBehavior = publishApi.publishBehavior _

  override val searchBehavior = searchApi.searchBehavior _

  override val projectBehavior = searchApi.projectBehavior _

  override val autocompleteBehavior = searchApi.autocompleteBehavior _

  override val versionBadgeBehavior = badges.versionBadgeBehavior _

  override val countBadgeBehavior = badges.countBadgeBehavior _

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