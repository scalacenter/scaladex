package scaladex.server.route.api

import scala.concurrent.ExecutionContext

import scaladex.core.api.Endpoints
import scaladex.core.api.ProjectResponse
import scaladex.core.model.*
import scaladex.core.service.ProjectService
import scaladex.core.service.SearchEngine
import scaladex.server.service.ArtifactService

import endpoints4s.pekkohttp.server
import org.apache.pekko.http.cors.scaladsl.CorsDirectives.cors
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

class ApiEndpointsImpl(projectService: ProjectService, artifactService: ArtifactService, searchEngine: SearchEngine)(
    using ExecutionContext
) extends Endpoints
    with server.Endpoints
    with server.JsonEntitiesFromSchemas:

  def routes(user: Option[UserState]): Route = cors()(concat(webApi(user), v0Api, v1Api))

  private def webApi(user: Option[UserState]): Route =
    autocomplete.implementedByAsync { params =>
      val searchParams = params.withUser(user)
      for projects <- searchEngine.autocomplete(searchParams, 5) yield projects.map(_.toAutocompletion)
    }

  private def v0Api: Route = concat(
    getProjects(v0).implementedByAsync(params =>
      projectService.getProjects(params.languages, params.platforms).unsafeToFuture()
    ),
    getProjectArtifacts(v0).implementedByAsync {
      case (ref, params) =>
        projectService
          .getArtifactRefs(ref, params.binaryVersion, params.artifactName, params.stableOnly)
          .unsafeToFuture()
    },
    getArtifactVersions(v0).implementedByAsync {
      case (groupId, artifactId, stableOnly) =>
        artifactService.getVersions(groupId, artifactId, stableOnly).unsafeToFuture()
    },
    getArtifact(v0).implementedByAsync { mavenRef =>
      artifactService.getArtifact(mavenRef).map(_.map(_.toResponse)).unsafeToFuture()
    }
  )

  private def v1Api: Route = concat(
    getProjects(v1).implementedByAsync(params =>
      projectService.getProjects(params.languages, params.platforms).unsafeToFuture()
    ),
    getProjectV1.implementedByAsync(ref => projectService.getProject(ref).map(_.map(toResponse)).unsafeToFuture()),
    getProjectVersionsV1.implementedByAsync {
      case (ref, params) =>
        projectService
          .getVersions(ref, params.binaryVersions, params.artifactNames, params.stableOnly)
          .unsafeToFuture()
    },
    getLatestProjectVersionV1.implementedByAsync(ref => projectService.getLatestProjectVersion(ref).unsafeToFuture()),
    getProjectVersionV1.implementedByAsync {
      case (ref, version) =>
        projectService.getProjectVersion(ref, version).unsafeToFuture()
    },
    getProjectArtifacts(v1).implementedByAsync {
      case (ref, params) =>
        projectService
          .getArtifactRefs(ref, params.binaryVersion, params.artifactName, params.stableOnly)
          .unsafeToFuture()
    },
    getLatestArtifactV1.implementedByAsync {
      case (groupId, artifactId) =>
        artifactService.getLatestArtifact(groupId, artifactId).map(_.map(_.toResponse)).unsafeToFuture()
    },
    getArtifactVersions(v1).implementedByAsync {
      case (groupId, artifactId, stableOnly) =>
        artifactService.getVersions(groupId, artifactId, stableOnly).unsafeToFuture()
    },
    getArtifact(v1).implementedByAsync { mavenRef =>
      artifactService.getArtifact(mavenRef).map(_.map(_.toResponse)).unsafeToFuture()
    }
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
