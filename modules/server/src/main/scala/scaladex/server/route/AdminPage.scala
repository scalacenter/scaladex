package scaladex.server.route
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import scaladex.core.model.Artifact
import scaladex.core.model.Env
import scaladex.core.model.Project
import scaladex.core.model.UserState
import scaladex.server.TwirlSupport._
import scaladex.server.service.AdminService
import scaladex.view
import scaladex.view.Task

class AdminPage(env: Env, adminService: AdminService) {

  def route(user: Option[UserState]): Route =
    pathPrefix("admin") {
      user match {
        case Some(user) if user.isAdmin(env) =>
          get {
            pathEnd {
              val jobs = adminService.allJobStatuses
              val tasks = adminService.allTaskStatuses
              val html = view.admin.html.admin(env, user, jobs, tasks)
              complete(html)
            }
          } ~
            post {
              path("jobs" / Segment / "start") { job =>
                adminService.startJob(job, user)
                redirect(Uri("/admin"), StatusCodes.SeeOther)
              }
            } ~
            post {
              path("jobs" / Segment / "stop") { job =>
                adminService.stopJob(job, user)
                redirect(Uri("/admin"), StatusCodes.SeeOther)
              }
            } ~
            post {
              path("tasks" / Task.findMissingArtifacts.name) {
                formFields("group-id", "artifact-name") { (rawGroupId, rawArtifactName) =>
                  val groupId = Artifact.GroupId(rawGroupId)
                  val artifactNameOpt = if (rawArtifactName.isEmpty) None else Some(Artifact.Name(rawArtifactName))
                  adminService.findMissingArtifacts(groupId, artifactNameOpt, user)
                  redirect(Uri("/admin"), StatusCodes.SeeOther)
                }
              }
            } ~
            post {
              path("tasks" / Task.updateGithubInfo.name) {
                formFields("organization", "repository") { (org, repo) =>
                  val reference = Project.Reference.from(org, repo)
                  adminService.updateGithubInfo(reference, user)
                  redirect(Uri("/admin"), StatusCodes.SeeOther)
                }
              }
            } ~
            post {
              path("tasks" / Task.addEmptyProject.name) {
                formFields("organization", "repository") { (org, repo) =>
                  val reference = Project.Reference.from(org, repo)
                  adminService.addEmptyProject(reference, user)
                  redirect(Uri("/admin"), StatusCodes.SeeOther)
                }
              }
            }
        case _ =>
          complete(StatusCodes.Forbidden, view.html.forbidden(env, user))
      }
    }

}
