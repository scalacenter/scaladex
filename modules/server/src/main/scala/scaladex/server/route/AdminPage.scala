package scaladex.server.route
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import scaladex.core.model.Artifact
import scaladex.core.model.Env
import scaladex.core.model.UserState
import scaladex.server.TwirlSupport._
import scaladex.server.service.AdminTaskService
import scaladex.server.service.JobService
import scaladex.view

class AdminPage(env: Env, jobService: JobService, adminTaskService: AdminTaskService) {

  def route(user: Option[UserState]): Route =
    pathPrefix("admin") {
      user match {
        case Some(user) if user.isAdmin(env) =>
          get {
            pathEnd {
              val schedulers = jobService.allStatuses
              val adminTasks = adminTaskService.getAllAdminTasks()
              val html = view.admin.html.admin(env, user, schedulers, adminTasks)
              complete(html)
            }
          } ~
            post {
              path(Segment / "start") { job =>
                jobService.start(job, user)
                redirect(Uri("/admin"), StatusCodes.SeeOther)
              }
            } ~
            post {
              path(Segment / "stop") { job =>
                jobService.stop(job, user)
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
