package scaladex.server.route.api

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import endpoints4s.akkahttp.server
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import scaladex.core.api.artifact.ArtifactEndpoints
import scaladex.core.api.artifact.ArtifactParams
import scaladex.core.api.artifact.ArtifactResponse
import scaladex.core.model.Language
import scaladex.core.model.Platform
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
            yield artifacts.map(artifact => ArtifactResponse(artifact.groupId.value, artifact.artifactId))
      }
    }
}

object ArtifactApi {

  implicit val formatArtifactResponse: OFormat[ArtifactResponse] =
    Json.format[ArtifactResponse]

  def apply(database: WebDatabase)(implicit ec: ExecutionContext): ArtifactApi =
    new ArtifactApi(database)
}
