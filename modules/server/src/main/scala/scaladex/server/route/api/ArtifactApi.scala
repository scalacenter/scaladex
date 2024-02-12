package scaladex.server.route.api

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.cors.scaladsl.CorsDirectives.cors
import endpoints4s.pekkohttp.server
import scaladex.core.api.artifact.ArtifactEndpoints
import scaladex.core.api.artifact.ArtifactMetadataParams
import scaladex.core.api.artifact.ArtifactMetadataResponse
import scaladex.core.api.artifact.ArtifactParams
import scaladex.core.api.artifact.ArtifactResponse
import scaladex.core.model.Artifact
import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.search.Page
import scaladex.core.model.search.Pagination
import scaladex.core.service.WebDatabase


class ArtifactApi(database: WebDatabase)(
    implicit ec: ExecutionContext
) extends ArtifactEndpoints
    with server.Endpoints
    with server.JsonEntitiesFromSchemas {

  val routes: Route =
    cors() {
      artifact.implementedByAsync {
        case ArtifactParams(maybeLanguageStr, maybePlatformStr) =>
          val maybeLanguage = maybeLanguageStr.flatMap(Language.fromLabel)
          val maybePlatform = maybePlatformStr.flatMap(Platform.fromLabel)
          for (artifacts <- database.getAllArtifacts(maybeLanguage, maybePlatform))
            yield {
              val distinctArtifacts =
                artifacts.map(artifact => ArtifactResponse(artifact.groupId.value, artifact.artifactId)).distinct
              // TODO: The values below are placeholders, will need to populate them w. real data.
              // See: https://github.com/scalacenter/scaladex/pull/992#discussion_r841500215
              Page(
                pagination = Pagination(
                  current = 1,
                  pageCount = 1,
                  totalSize = distinctArtifacts.size.toLong
                ),
                items = distinctArtifacts
              )
            }
      } ~ artifactMetadata.implementedByAsync {
        case ArtifactMetadataParams(groupId, artifactId) =>
          val parsedGroupId = Artifact.GroupId(groupId)
          Artifact.ArtifactId.parse(artifactId).fold(Future.successful(Page.empty[ArtifactMetadataResponse])) {
            parsedArtifactId =>
              val futureArtifacts = database.getArtifacts(parsedGroupId, parsedArtifactId)
              val futureResponses = futureArtifacts.map(_.map(Artifact.toMetadataResponse))
              futureResponses.map { resp =>
                // TODO: The values below are placeholders, will need to populate them w. real data.
                // See: https://github.com/scalacenter/scaladex/pull/992#discussion_r841500215
                Page(
                  pagination = Pagination(
                    current = 1,
                    pageCount = 1,
                    totalSize = resp.size
                  ),
                  items = resp
                )
              }
          }
      }
    }
}

object ArtifactApi {

  def apply(database: WebDatabase)(implicit ec: ExecutionContext): ArtifactApi =
    new ArtifactApi(database)
}
