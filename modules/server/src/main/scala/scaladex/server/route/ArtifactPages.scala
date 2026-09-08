package scaladex.server.route

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import scaladex.core.model.{Artifact, Env, Project, UserState}
import scaladex.core.service.WebDatabase
import scaladex.server.TwirlSupport.given
import scaladex.server.service.ScaladocService
import scaladex.view.html
import scaladex.view.project.html.scaladoc

import scala.concurrent.ExecutionContext
import scala.util.Success

class ArtifactPages(env: Env, database: WebDatabase, scaladocService: ScaladocService)(using ExecutionContext)
    extends LazyLogging:
  def route(user: Option[UserState]): Route = concat(
    get {
      // The Scaladex-branded shell: a thin bar + a full-height iframe pointing at scaladoc-content below.
      path("artifacts" / artifactRefM / "scaladoc") { ref =>
        val resultF = for
          artifact <- database.getArtifact(ref).map(_.get)
          project <- database.getProject(artifact.projectRef)
        yield project.map(p => (p, artifact))

        onComplete(resultF) {
          case Success(Some((project, artifact))) =>
            complete(scaladoc(project, artifact))
          case _ =>
            complete(StatusCodes.NotFound, html.notfound(env, user))
        }
      }
    },
    get {
      // The raw scaladoc content, iframed by the page above. Serves a locally-unpacked copy of the
      // artifact's -javadoc.jar when we have (or can fetch) one, falling back to a redirect to the
      // project's actual external scaladoc link otherwise - same target as the old bare redirect here.
      pathPrefix("artifacts" / artifactRefM / "scaladoc-content") { ref =>
        val resultF = for
          artifact <- database.getArtifact(ref).map(_.get)
          project <- database.getProject(artifact.projectRef)
          available <- scaladocService.ensureAvailable(artifact.reference)
        yield (project, artifact, available)

        onComplete(resultF) {
          case Success((_, artifact, true)) =>
            val localDir = scaladocService.localDir(artifact.reference)
            concat(
              pathEndOrSingleSlash(getFromFile(localDir.resolve("index.html").toFile)),
              getFromDirectory(localDir.toString)
            )
          case Success((project, artifact, false)) =>
            redirectToExternalScaladoc(project, artifact, user)
          case _ =>
            complete(StatusCodes.NotFound, html.notfound(env, user))
        }
      }
    }
  )

  private def redirectToExternalScaladoc(
      project: Option[Project],
      artifact: Artifact,
      user: Option[UserState]
  ): Route = extractUnmatchedPath { dri =>
    (for
      p <- project
      doc <- p.scaladoc(artifact)
      uri = Uri(doc.link)
      finalUri = uri.withPath(uri.path ++ dri)
    yield redirect(finalUri, StatusCodes.SeeOther)).getOrElse(complete(StatusCodes.NotFound, html.notfound(env, user)))
  }
end ArtifactPages
