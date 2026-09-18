package scaladex.server.route.api

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.*

import scaladex.core.api.Endpoints
import scaladex.core.api.ProjectResponse
import scaladex.core.api.SearchResult
import scaladex.core.api.UserResponse
import scaladex.core.model.*
import scaladex.core.model.search.ProjectDocument
import scaladex.core.service.GithubAuth
import scaladex.core.service.ProjectService
import scaladex.core.service.SearchEngine
import scaladex.core.util.Secret
import scaladex.server.service.ArtifactService
import scaladex.server.service.ProjectSettingsService

import com.github.blemale.scaffeine.AsyncLoadingCache
import com.github.blemale.scaffeine.Scaffeine
import endpoints4s.algebra.BasicAuthentication.Credentials
import endpoints4s.pekkohttp.server
import org.apache.pekko.http.cors.scaladsl.CorsDirectives.cors
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

class ApiEndpointsImpl(
    env: Env,
    projectService: ProjectService,
    artifactService: ArtifactService,
    settingsService: ProjectSettingsService,
    searchEngine: SearchEngine,
    githubAuth: GithubAuth
)(
    using ExecutionContext
) extends Endpoints
    with server.Endpoints
    with server.JsonEntitiesFromSchemas
    with server.BasicAuthentication:

  def routes(user: Option[UserState]): Route = cors()(concat(webApi(user), v0Api, v1Api, v1AuthApi))

  private val userStateCache: AsyncLoadingCache[Secret, Option[UserState]] =
    Scaffeine()
      .expireAfterWrite(10.minutes)
      .maximumSize(4096)
      .buildAsyncFuture(token => githubAuth.getUserState(token))

  private def resolveUser(credentials: Credentials): Future[Option[UserState]] =
    userStateCache.get(Secret(credentials.password))

  // None -> 403, Some(None) -> 404 (unknown project, reported even without edit permission), Some(Some(a)) -> 200.
  private def withEditableProject[A](credentials: Credentials, ref: Project.Reference)(
      f: Project.Settings => Future[A]
  ): Future[Option[Option[A]]] =
    resolveUser(credentials).flatMap {
      case Some(user) if user.canEdit(ref, env) =>
        projectService.getSettings(ref).flatMap {
          case None => Future.successful(Some(None))
          case Some(settings) => f(settings).map(a => Some(Some(a)))
        }
      case _ =>
        projectService.getProject(ref).map {
          case None => Some(None)
          case Some(_) => None
        }
    }

  private def webApi(user: Option[UserState]): Route =
    autocomplete.implementedByAsync { params =>
      val searchParams = params.withUser(user)
      for projects <- searchEngine.autocomplete(searchParams, 5) yield projects.map(_.toAutocompletion)
    }

  private def v0Api: Route = concat(
    getProjects(v0).implementedByAsync(params => projectService.getProjects(params.languages, params.platforms)),
    getProjectArtifacts(v0).implementedByAsync {
      case (ref, params) =>
        projectService.getArtifactRefs(ref, params.binaryVersion, params.artifactName, params.stableOnly)
    },
    getArtifactVersions(v0).implementedByAsync {
      case (groupId, artifactId, stableOnly) =>
        artifactService.getVersions(groupId, artifactId, stableOnly)
    },
    getArtifact(v0).implementedByAsync { mavenRef =>
      for artifact <- artifactService.getArtifact(mavenRef) yield artifact.map(_.toResponse)
    }
  )

  private def v1Api: Route = concat(
    getProjects(v1).implementedByAsync(params => projectService.getProjects(params.languages, params.platforms)),
    getProjectV1.implementedByAsync(ref => for project <- projectService.getProject(ref) yield project.map(toResponse)),
    getProjectVersionsV1.implementedByAsync {
      case (ref, params) =>
        projectService.getVersions(ref, params.binaryVersions, params.artifactNames, params.stableOnly)
    },
    getLatestProjectVersionV1.implementedByAsync(ref => projectService.getLatestProjectVersion(ref)),
    getProjectVersionV1.implementedByAsync { case (ref, version) => projectService.getProjectVersion(ref, version) },
    getProjectArtifacts(v1).implementedByAsync {
      case (ref, params) =>
        projectService.getArtifactRefs(ref, params.binaryVersion, params.artifactName, params.stableOnly)
    },
    getProjectDependenciesV1.implementedByAsync {
      case (ref, version, page) => projectService.getDependencies(ref, version, page)
    },
    getProjectDependentsV1.implementedByAsync { case (ref, page) => projectService.getDependents(ref, page) },
    searchProjectsV1.implementedByAsync {
      case (params, page) =>
        for results <- searchEngine.find(params.toSearchParams, page)
        yield results.map(hit => toSearchResult(hit.document))
    },
    getLatestArtifactV1.implementedByAsync {
      case (groupId, artifactId) =>
        for artifact <- artifactService.getLatestArtifact(groupId, artifactId) yield artifact.map(_.toResponse)
    },
    getArtifactVersions(v1).implementedByAsync {
      case (groupId, artifactId, stableOnly) =>
        artifactService.getVersions(groupId, artifactId, stableOnly)
    },
    getArtifact(v1).implementedByAsync { mavenRef =>
      for artifact <- artifactService.getArtifact(mavenRef) yield artifact.map(_.toResponse)
    }
  )

  private def v1AuthApi: Route = concat(
    getAuthenticatedUserV1.implementedByAsync { credentials => resolveUser(credentials).map(_.map(toUserResponse)) },
    getProjectSettingsV1.implementedByAsync {
      case (ref, credentials) =>
        withEditableProject(credentials, ref)(Future.successful)
    },
    patchProjectSettingsV1.implementedByAsync {
      case (ref, patch, credentials) =>
        withEditableProject(credentials, ref)(current => settingsService.updateSettings(ref, patch.applyTo(current)))
    }
  )

  private def toUserResponse(user: UserState): UserResponse =
    UserResponse(
      login = user.info.login,
      name = user.info.name,
      avatarUrl = user.info.avatarUrl,
      isAdmin = user.isAdmin(env),
      organizations = user.orgs.toSeq.sortBy(_.value),
      repositories = user.repos.toSeq.sortBy(_.toString)
    )

  private def toSearchResult(document: ProjectDocument): SearchResult =
    SearchResult(
      organization = document.organization,
      repository = document.repository,
      description = document.githubInfo.flatMap(_.description),
      stars = document.githubInfo.flatMap(_.stars),
      forks = document.githubInfo.flatMap(_.forks),
      topics = document.githubInfo.map(_.topics).getOrElse(Seq.empty),
      languages = document.languages,
      platforms = document.platforms,
      latestVersion = document.latestVersion,
      dependents = document.dependents,
      category = document.category.map(_.label)
    )

  private def toResponse(project: Project): ProjectResponse =
    import project.*
    import settings.*
    ProjectResponse(
      organization,
      repository,
      githubInfo.flatMap(_.homepage),
      githubInfo.flatMap(_.description),
      githubInfo.flatMap(_.logo),
      githubInfo.flatMap(_.stars),
      githubInfo.flatMap(_.forks),
      githubInfo.flatMap(_.issues),
      githubInfo.toSet.flatMap((s: GithubInfo) => s.topics),
      githubInfo.flatMap(_.contributingGuide),
      githubInfo.flatMap(_.codeOfConduct),
      githubInfo.flatMap(_.license),
      defaultArtifact,
      customScalaDoc,
      documentationLinks,
      contributorsWanted,
      cliArtifacts,
      category,
      chatroom
    )
  end toResponse
end ApiEndpointsImpl
