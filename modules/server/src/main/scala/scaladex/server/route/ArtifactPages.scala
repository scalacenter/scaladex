package scaladex.server.route

import scala.concurrent.ExecutionContext
import scala.util.Success

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Env
import scaladex.core.model.UserState
import scaladex.core.service.WebDatabase
import scaladex.server.TwirlSupport._
import scaladex.view.html

class ArtifactPages(env: Env, database: WebDatabase)(implicit executionContext: ExecutionContext) extends LazyLogging {

  def route(user: Option[UserState]): Route =
    path("artifacts" / mavenReferenceM / "scaladoc" ~ RemainingPath) { (mavenRef, dri) =>
      val scaladocUriF = for {
        artifact <- database.getArtifactByMavenReference(mavenRef).map(_.get)
        project <- database.getProject(artifact.projectRef)
      } yield artifact.scaladoc(project.flatMap(_.settings.customScalaDoc)).map(Uri.apply)

      onComplete(scaladocUriF) {
        case Success(Some(scaladocUri)) =>
          val finalUri = scaladocUri.withPath(scaladocUri.path ++ dri)
          redirect(finalUri, StatusCodes.SeeOther)
        case _ =>
          complete(StatusCodes.NotFound, html.notfound(env, user))
      }
    }
}
