package scaladex.server.route.api

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import endpoints4s.akkahttp.server
import scaladex.core.api.artifact.ArtifactEndpoints
import scaladex.core.api.artifact.ArtifactParams
import scaladex.core.api.artifact.ArtifactResponse
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
                  current = 0,
                  pageCount = 0,
                  totalSize = distinctArtifacts.size.toLong
                ),
                items = distinctArtifacts
              )
            }
      }
    }
}

object ArtifactApi {

  def apply(database: WebDatabase)(implicit ec: ExecutionContext): ArtifactApi =
    new ArtifactApi(database)
}
