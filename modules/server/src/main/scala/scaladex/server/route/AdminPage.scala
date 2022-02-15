package scaladex.server.route

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import scaladex.core.model.Env
import scaladex.core.model.UserState
import scaladex.server.TwirlSupport._
import scaladex.server.service.SchedulerService
import scaladex.view

class AdminPage(env: Env, schedulerSrv: SchedulerService)(implicit ec: ExecutionContext) {

  def route(user: Option[UserState]): Route =
    pathPrefix("admin") {
      user match {
        case Some(user) if user.isAdmin =>
          get {
            pathEnd {
              val schedulers = schedulerSrv.getSchedulers()
              val html = view.admin.html.admin(env, user, schedulers)
              complete(html)
            }
          } ~
            post {
              path(Segment / "start") { schedulerName =>
                schedulerSrv.start(schedulerName)
                redirect(Uri("/admin"), StatusCodes.SeeOther)
              }
            } ~
            post {
              path(Segment / "stop") { schedulerName =>
                schedulerSrv.stop(schedulerName)
                redirect(Uri("/admin"), StatusCodes.SeeOther)
              }
            }
        case _ =>
          complete(StatusCodes.Forbidden, view.html.forbidden(env, user))
      }
    }
}
