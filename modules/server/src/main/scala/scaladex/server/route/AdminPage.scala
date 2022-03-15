package scaladex.server.route

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import scaladex.core.model.Artifact
import scaladex.core.model.Env
import scaladex.core.model.UserState
import scaladex.server.TwirlSupport._
import scaladex.server.service.AdminTaskService
import scaladex.server.service.SchedulerService
import scaladex.view

class AdminPage(env: Env, schedulerSrv: SchedulerService, adminTaskService: AdminTaskService)(
    implicit ec: ExecutionContext
) {

  def route(user: Option[UserState]): Route =
    pathPrefix("admin") {
      user match {
        case Some(user) if user.isAdmin(env) =>
          get {
            pathEnd {
              val schedulers = schedulerSrv.getSchedulers()
              val adminTasks = adminTaskService.getAllAdminTasks()
              val html = view.admin.html.admin(env, user, schedulers, adminTasks)
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
            } ~
            path("index") {
              formFields("group-id", "artifact-name") { (groupIdString, artifactNameString) =>
                val groupId = Artifact.GroupId(groupIdString)
                val artifactNameOpt = if (artifactNameString.isEmpty) None else Some(Artifact.Name(artifactNameString))
                adminTaskService.indexArtifacts(groupId, artifactNameOpt, user.info.login)
                redirect(Uri("/admin"), StatusCodes.SeeOther)
              }
            }
        case _ =>
          complete(StatusCodes.Forbidden, view.html.forbidden(env, user))
      }
    }

}
