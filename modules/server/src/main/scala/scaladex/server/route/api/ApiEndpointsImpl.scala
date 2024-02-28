package scaladex.server.route.api

import scala.concurrent.ExecutionContext

import endpoints4s.pekkohttp.server
import org.apache.pekko.http.cors.scaladsl.CorsDirectives.cors
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import scaladex.core.api.ArtifactResponse
import scaladex.core.api.AutocompletionResponse
import scaladex.core.api.Endpoints
import scaladex.core.model.UserState
import scaladex.core.service.SearchEngine
import scaladex.core.service.WebDatabase

class ApiEndpointsImpl(database: WebDatabase, searchEngine: SearchEngine)(
    implicit ec: ExecutionContext
) extends Endpoints
    with server.Endpoints
    with server.JsonEntitiesFromSchemas {

  def routes(user: Option[UserState]): Route =
    cors()(
      concat(
        listProjects.implementedByAsync { _ =>
          for (projectStatuses <- database.getAllProjectsStatuses()) yield projectStatuses.iterator.collect {
            case (ref, status) if status.isOk || status.isFailed || status.isUnknown => ref
          }.toSeq
        },
        listProjectArtifacts.implementedByAsync { ref =>
          for (artifacts <- database.getArtifacts(ref))
            yield artifacts.map(_.mavenReference)
        },
        getArtifact.implementedByAsync { mavenRef =>
          for (artifactOpt <- database.getArtifactByMavenReference(mavenRef))
            yield artifactOpt.map(ArtifactResponse.apply)
        },
        autocomplete.implementedByAsync { params =>
          val searchParams = params.withUser(user)
          for (projects <- searchEngine.autocomplete(searchParams, 5))
            yield projects.map { project =>
              AutocompletionResponse(
                project.organization.value,
                project.repository.value,
                project.githubInfo.flatMap(_.description).getOrElse("")
              )
            }
        }
      )
    )

}
