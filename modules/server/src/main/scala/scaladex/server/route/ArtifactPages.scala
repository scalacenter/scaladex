package scaladex.server.route

import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success

import scaladex.core.model.Artifact
import scaladex.core.model.Env
import scaladex.core.model.Project
import scaladex.core.model.UserState
import scaladex.core.service.WebDatabase
import scaladex.server.TwirlSupport.given
import scaladex.server.service.ScaladocService
import scaladex.view.html
import scaladex.view.project.html.scaladoc

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.server.directives.ContentTypeResolver

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
          case Success((_, artifact, true)) => serveScaladocFile(artifact.reference)
          case Success((project, artifact, false)) =>
            redirectToExternalScaladoc(project, artifact, user)
          case _ =>
            complete(StatusCodes.NotFound, html.notfound(env, user))
        }
      }
    }
  )

  private def serveScaladocFile(ref: Artifact.Reference): Route = extractUnmatchedPath { path =>
    onComplete(scaladocService.getFile(ref, decode(path))) {
      case Success(Some((name, bytes))) =>
        // a given artifact version's scaladoc never changes
        respondWithHeader(
          headers.`Cache-Control`(headers.CacheDirectives.public, headers.CacheDirectives.`max-age`(86400))
        ) {
          complete(HttpEntity(ContentTypeResolver.Default(name), bytes))
        }
      case Success(None) => complete(StatusCodes.NotFound)
      case Failure(exception) =>
        logger.warn(s"Failed to read scaladoc file $path of $ref", exception)
        complete(StatusCodes.InternalServerError)
    }
  }

  private def decode(path: Uri.Path): String = path match
    case Uri.Path.Empty => ""
    case Uri.Path.Slash(tail) => "/" + decode(tail)
    case Uri.Path.Segment(head, tail) => head + decode(tail)

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
