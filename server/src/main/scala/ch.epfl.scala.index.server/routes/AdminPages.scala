package ch.epfl.scala.index.server.routes

import java.util.UUID

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.StandardRoute
import ch.epfl.scala.index.model.misc.UserState
import ch.epfl.scala.index.server.GithubUserSession
import ch.epfl.scala.index.server.TwirlSupport._
import ch.epfl.scala.index.views
import com.softwaremill.session.SessionDirectives.optionalSession
import com.softwaremill.session.SessionOptions.refreshable
import com.softwaremill.session.SessionOptions.usingCookies
import scaladex.server.service.SchedulerService

class AdminPages(schedulerSrv: SchedulerService, session: GithubUserSession)(
    implicit ec: ExecutionContext
) {

  def ifAdmin(
      userId: Option[UUID]
  )(res: UserState => StandardRoute): StandardRoute =
    session.getUser(userId) match {
      case Some(userState) if userState.isAdmin =>
        res(userState)
      case maybeUser =>
        complete(StatusCodes.Forbidden, views.html.forbidden(maybeUser))
    }

  val routes: Route = {
    import session.implicits._

    pathPrefix("admin") {
      concat(
        get {
          pathEndOrSingleSlash {
            optionalSession(refreshable, usingCookies)(userId =>
              ifAdmin(userId) { userState =>
                val scheduler = schedulerSrv.getScheduler()
                val html = views.admin.html.admin(
                  userState,
                  scheduler.name,
                  scheduler.status
                )
                complete(html)
              }
            )
          }
        },
        post {
          path(Segment / "start") { schedulerName =>
            optionalSession(refreshable, usingCookies)(userId =>
              ifAdmin(userId) { userState =>
                schedulerSrv.start(schedulerName)
                val scheduler = schedulerSrv.getScheduler()
                val html = views.admin.html
                  .admin(userState, scheduler.name, scheduler.status)
                complete(html)
              }
            )
          }
        },
        post {
          path(Segment / "stop") { schedulerName =>
            optionalSession(refreshable, usingCookies)(userId =>
              ifAdmin(userId) { userState =>
                schedulerSrv.stop(schedulerName)
                val scheduler = schedulerSrv.getScheduler()
                val html = views.admin.html
                  .admin(userState, scheduler.name, scheduler.status)
                complete(html)
              }
            )
          }
        }
      )
    }
  }
}
