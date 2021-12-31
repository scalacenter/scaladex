package scaladex.server.route

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import akka.http.scaladsl.model.Uri._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import com.typesafe.scalalogging.LazyLogging
import play.twirl.api.HtmlFormat
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactSelection
import scaladex.core.model.Env
import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.core.model.SemanticVersion
import scaladex.core.model.UserState
import scaladex.core.service.LocalStorageApi
import scaladex.core.service.WebDatabase
import scaladex.core.util.ScalaExtensions._
import scaladex.infra.storage.DataPaths
import scaladex.server.GithubUserSession
import scaladex.server.TwirlSupport._
import scaladex.view

class ProjectPages(
    env: Env,
    database: WebDatabase,
    localStorage: LocalStorageApi,
    session: GithubUserSession,
    paths: DataPaths
)(implicit executionContext: ExecutionContext)
    extends LazyLogging {
  import session.implicits._

  private def getEditPage(
      projectRef: Project.Reference,
      userInfo: UserState
  ): Future[(StatusCode, HtmlFormat.Appendable)] =
    for {
      projectOpt <- database.findProject(projectRef)
      releases <- database.findReleases(projectRef)
    } yield projectOpt
      .map { p =>
        val page = view.project.html.editproject(env, p, releases, Some(userInfo))
        (StatusCodes.OK, page)
      }
      .getOrElse((StatusCodes.NotFound, view.html.notfound(env, Some(userInfo))))

  private def filterVersions(
      p: Project,
      allVersions: Seq[SemanticVersion]
  ): Seq[SemanticVersion] =
    (if (p.settings.strictVersions) allVersions.filter(_.isSemantic)
     else allVersions).distinct.sorted.reverse

  private def getProjectPage(
      organization: Project.Organization,
      repository: Project.Repository,
      target: Option[String],
      artifact: Artifact.Name,
      version: Option[SemanticVersion],
      user: Option[UserState]
  ): Future[(StatusCode, HtmlFormat.Appendable)] = {
    val selection = ArtifactSelection.parse(
      platform = target,
      artifactName = Some(artifact),
      version = version.map(_.toString),
      selected = None
    )
    val projectRef =
      Project.Reference(organization, repository)

    database.findProject(projectRef).flatMap {
      case Some(project) =>
        for {
          releases <- database.findReleases(projectRef)
          // the selected Release
          selectedRelease <- selection
            .filterReleases(releases, project)
            .headOption
            .toFuture(new Exception(s"no release found for $projectRef"))
          directDependencies <- database.findDirectDependencies(selectedRelease)
          reverseDependency <- database.findReverseDependencies(selectedRelease)
          // compute stuff
          allVersions = releases.map(_.version)
          filteredVersions = filterVersions(project, allVersions)
          platforms = releases.map(_.platform).distinct.sorted.reverse
          artifactNames = releases.map(_.artifactName).distinct.sortBy(_.value)
          twitterCard = project.twitterSummaryCard
        } yield (
          StatusCodes.OK,
          view.project.html.project(
            env,
            project,
            artifactNames,
            filteredVersions,
            platforms,
            selectedRelease,
            user,
            canEdit = true,
            Some(twitterCard),
            releases.size,
            directDependencies,
            reverseDependency
          )
        )
      case None =>
        Future.successful((StatusCodes.NotFound, view.html.notfound(env, user)))
    }
  }

  val routes: Route =
    concat(
      post(
        path("edit" / organizationM / repositoryM)((organization, repository) =>
          optionalSession(refreshable, usingCookies)(_ =>
            pathEnd(
              editForm { form =>
                val ref = Project.Reference(organization, repository)
                val updated = for {
                  _ <- localStorage.saveProjectSettings(ref, form)
                  updated <- database.updateProjectSettings(ref, form)
                } yield updated
                onComplete(updated) {
                  case Success(()) =>
                    redirect(
                      Uri(s"/$organization/$repository"),
                      StatusCodes.SeeOther
                    )
                  case Failure(e) =>
                    println(s"error sorry ${e.getMessage()}")
                    redirect(
                      Uri(s"/$organization/$repository"),
                      StatusCodes.SeeOther
                    ) // maybe we can print that it wasn't saved
                }
              }
            )
          )
        )
      ),
      get {
        path("artifacts" / organizationM / repositoryM)((org, repo) =>
          optionalSession(refreshable, usingCookies) { userId =>
            val user = session.getUser(userId)
            val ref = Project.Reference(org, repo)
            val res =
              for {
                projectOpt <- database.findProject(ref)
                project <- projectOpt.toFuture(
                  new Exception(s"project ${ref} not found")
                )
                releases <- database.findReleases(project.reference)
                // some computation
                targetTypesWithScalaVersion = releases
                  .groupBy(_.platform.platformType)
                  .map {
                    case (targetType, releases) =>
                      (
                        targetType,
                        releases
                          .map(_.fullPlatformVersion)
                          .distinct
                          .sorted
                          .reverse
                      )
                  }
                artifactsWithVersions = releases
                  .groupBy(_.version)
                  .map {
                    case (semanticVersion, releases) =>
                      (
                        semanticVersion,
                        releases.groupBy(_.artifactName).map {
                          case (artifactName, releases) =>
                            (
                              artifactName,
                              releases.map(r => (r, r.fullPlatformVersion))
                            )
                        }
                      )
                  }
                  .toSeq
                  .sortBy(_._1)
                  .reverse
              } yield (
                project,
                targetTypesWithScalaVersion,
                artifactsWithVersions
              )

            onComplete(res) {
              case Success(
                    (
                      project,
                      targetTypesWithScalaVersion,
                      artifactsWithVersions
                    )
                  ) =>
                complete(
                  view.html.artifacts(
                    env,
                    project,
                    user,
                    targetTypesWithScalaVersion,
                    artifactsWithVersions
                  )
                )
              case Failure(e) =>
                complete(StatusCodes.NotFound, view.html.notfound(env, user))

            }
          }
        )
      },
      get {
        path("edit" / organizationM / repositoryM)((organization, repository) =>
          optionalSession(refreshable, usingCookies)(userId =>
            pathEnd {
              val projectRef =
                Project.Reference(organization, repository)
              session.getUser(userId) match {
                case Some(userState) if userState.canEdit(projectRef) =>
                  complete(getEditPage(projectRef, userState))
                case maybeUser =>
                  complete(
                    (
                      StatusCodes.Forbidden,
                      view.html.forbidden(env, maybeUser)
                    )
                  )
              }
            }
          )
        )
      },
      get {
        path(organizationM / repositoryM)((organization, repository) =>
          optionalSession(refreshable, usingCookies)(userId =>
            parameters(("artifact".?, "version".?, "target".?, "selected".?)) { (artifact, version, target, selected) =>
              val projectRef = Project.Reference(organization, repository)
              val fut: Future[StandardRoute] = database.findProject(projectRef).flatMap {
                case Some(Project(_, _, _, GithubStatus.Moved(_, newProjectRef), _, _)) =>
                  Future.successful(redirect(Uri(s"/$newProjectRef"), StatusCodes.PermanentRedirect))
                case Some(project) =>
                  val releaseFut: Future[StandardRoute] =
                    getSelectedRelease(
                      database,
                      project,
                      platform = target,
                      artifact = artifact.map(Artifact.Name.apply),
                      version = version,
                      selected = selected
                    ).map(_.map { artifact =>
                      val targetParam = s"?target=${artifact.platform.encode}"
                      redirect(
                        s"/$organization/$repository/${artifact.artifactName}/${artifact.version}/$targetParam",
                        StatusCodes.TemporaryRedirect
                      )
                    }.getOrElse(complete(StatusCodes.NotFound, view.html.notfound(env, session.getUser(userId)))))
                  releaseFut
                case None =>
                  Future.successful(
                    complete(StatusCodes.NotFound, view.html.notfound(env, session.getUser(userId)))
                  )
              }

              onSuccess(fut)(identity)
            }
          )
        )
      },
      get {
        path(organizationM / repositoryM / artifactM)((organization, repository, artifact) =>
          optionalSession(refreshable, usingCookies)(userId =>
            parameter("target".?) { target =>
              val user = session.getUser(userId)
              val res = getProjectPage(
                organization,
                repository,
                target,
                artifact,
                None,
                user
              )
              onComplete(res) {
                case Success((code, some)) => complete(code, some)
                case Failure(e) =>
                  complete(StatusCodes.NotFound, view.html.notfound(env, user))
              }
            }
          )
        )
      },
      get {
        path(organizationM / repositoryM / artifactM / versionM)((organization, repository, artifact, version) =>
          optionalSession(refreshable, usingCookies) { userId =>
            parameter("target".?) { target =>
              val user = session.getUser(userId)
              val res = getProjectPage(
                organization,
                repository,
                target,
                artifact,
                Some(version),
                user
              )
              onComplete(res) {
                case Success((code, some)) => complete(code, some)
                case Failure(e) =>
                  complete(StatusCodes.NotFound, view.html.notfound(env, user))
              }
            }
          }
        )
      }
    )
}
