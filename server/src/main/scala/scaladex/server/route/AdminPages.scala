package scaladex.server.route

import java.util.UUID

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.StandardRoute
import com.softwaremill.session.SessionDirectives.optionalSession
import com.softwaremill.session.SessionOptions.refreshable
import com.softwaremill.session.SessionOptions.usingCookies
import scaladex.core.model.UserState
import scaladex.server.GithubUserSession
import scaladex.server.TwirlSupport._
import scaladex.server.service.SchedulerService
import scaladex.view

class AdminPages(production: Boolean, schedulerSrv: SchedulerService, session: GithubUserSession)(
    implicit ec: ExecutionContext
) {

  def ifAdmin(
      userId: Option[UUID]
  )(res: UserState => StandardRoute): StandardRoute =
    session.getUser(userId) match {
      case Some(userState) if userState.isAdmin =>
        res(userState)
      case maybeUser =>
        complete(StatusCodes.Forbidden, view.html.forbidden(production, maybeUser))
    }

  val routes: Route = {
    import session.implicits._

    pathPrefix("admin") {
      concat(
        get {
          pathEndOrSingleSlash {
            optionalSession(refreshable, usingCookies)(userId =>
              ifAdmin(userId) { userState =>
                val schedulers = schedulerSrv.getSchedulers()
                val html = view.admin.html.admin(production, userState, schedulers)
                complete(html)
              }
            )
          }
        },
        post {
          path(Segment / "start") { schedulerName =>
            optionalSession(refreshable, usingCookies)(userId =>
              ifAdmin(userId) { _ =>
                schedulerSrv.start(schedulerName)
                redirect(Uri("/admin"), StatusCodes.SeeOther)
              }
            )
          }
        },
        post {
          path(Segment / "stop") { schedulerName =>
            optionalSession(refreshable, usingCookies)(userId =>
              ifAdmin(userId) { _ =>
                schedulerSrv.stop(schedulerName)
                redirect(Uri("/admin"), StatusCodes.SeeOther)
              }
            )
          }
        }
      )
    }
  }
}
