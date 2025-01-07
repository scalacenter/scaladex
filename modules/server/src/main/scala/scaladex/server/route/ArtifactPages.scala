package scaladex.server.route

import scala.concurrent.ExecutionContext
import scala.util.Success

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import scaladex.core.model.Env
import scaladex.core.model.UserState
import scaladex.core.service.WebDatabase
import scaladex.server.TwirlSupport.*
import scaladex.view.html

class ArtifactPages(env: Env, database: WebDatabase)(implicit executionContext: ExecutionContext) extends LazyLogging:
  def route(user: Option[UserState]): Route =
    concat(
      get {
        path("artifacts" / artifactRefM / "scaladoc" ~ RemainingPath) { (ref, dri) =>
          val scaladocUriF = for
            artifact <- database.getArtifact(ref).map(_.get)
            project <- database.getProject(artifact.projectRef)
          yield project.flatMap(_.scaladoc(artifact).map(doc => Uri(doc.link)))

          onComplete(scaladocUriF) {
            case Success(Some(scaladocUri)) =>
              val finalUri = scaladocUri.withPath(scaladocUri.path ++ dri)
              redirect(finalUri, StatusCodes.SeeOther)
            case _ =>
              complete(StatusCodes.NotFound, html.notfound(env, user))
          }
        }
      }
    )
end ArtifactPages
