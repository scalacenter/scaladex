package ch.epfl.scala.index
package server
package routes

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import akka.http.scaladsl.model.Uri._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import ch.epfl.scala.index.data.github.GithubReader
import ch.epfl.scala.index.data.github.Json4s
import ch.epfl.scala.index.model._
import ch.epfl.scala.index.model.misc._
import ch.epfl.scala.index.model.release._
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.server.TwirlSupport._
import ch.epfl.scala.services.DatabaseApi
import ch.epfl.scala.services.LocalStorageApi
import ch.epfl.scala.services.storage.DataPaths
import ch.epfl.scala.utils.ScalaExtensions._
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import com.typesafe.scalalogging.LazyLogging
import org.json4s.native.Serialization.write
import play.twirl.api.HtmlFormat

class ProjectPages(
    db: DatabaseApi,
    localStorage: LocalStorageApi,
    session: GithubUserSession,
    paths: DataPaths
)(implicit executionContext: ExecutionContext)
    extends LazyLogging {
  import session.implicits._

  private def getEditPage(
      projectRef: Project.Reference,
      userInfo: UserInfo
  ): Future[(StatusCode, HtmlFormat.Appendable)] = {
    for {
      projectOpt <- db.findProject(projectRef)
      releases <- db.findReleases(projectRef)
    } yield {
      projectOpt
        .map { p =>
          val beginnerIssuesJson = p.githubInfo
            .map { github =>
              import Json4s._
              write[List[GithubIssue]](github.beginnerIssues)
            }
            .getOrElse("")
          (
            StatusCodes.OK,
            views.project.html.editproject(
              p,
              releases,
              Some(userInfo),
              beginnerIssuesJson
            )
          )
        }
        .getOrElse((StatusCodes.NotFound, views.html.notfound(Some(userInfo))))
    }
  }

  private def getSelectedRelease(
      org: NewProject.Organization,
      repo: NewProject.Repository,
      target: Option[String],
      artifact: Option[String],
      version: Option[String],
      selected: Option[String]
  ): Future[Option[NewRelease]] = {
    val releaseSelection = ReleaseSelection.parse(
      target = target,
      artifactName = artifact,
      version = version,
      selected = selected
    )
    val projectRef = Project.Reference(org.value, repo.value)
    for {
      project <- db.findProject(projectRef)
      releases <- db.findReleases(projectRef)
      filteredReleases = project
        .map(p => ReleaseOptions.filterReleases(releaseSelection, releases, p))
        .getOrElse(Nil)
    } yield filteredReleases.headOption
  }

  private def filterVersions(
      p: NewProject,
      allVersions: Seq[SemanticVersion]
  ): Seq[SemanticVersion] = {
    if (p.dataForm.strictVersions) allVersions.filter(_.isSemantic)
    else allVersions
  }

  private def getProjectPage(
      organization: NewProject.Organization,
      repository: NewProject.Repository,
      target: Option[String],
      artifact: NewRelease.ArtifactName,
      version: Option[SemanticVersion],
      user: Option[UserInfo]
  ): Future[(StatusCode, HtmlFormat.Appendable)] = {
    val selection = ReleaseSelection.parse(
      target = target,
      artifactName = Some(artifact.value),
      version = version.map(_.toString),
      selected = None
    )
    val projectRef =
      Project.Reference(organization.value, repository.value)

    db.findProject(projectRef).flatMap {
      case Some(project) =>
        for {
          releases <- db.findReleases(projectRef)
          dependencies =
            Seq() // trouver toute les dependences de project
          // the selected Release
          selectedRelease <- ReleaseOptions
            .filterReleases(
              selection,
              releases,
              project
            )
            .headOption
            .toFuture(new Exception(s"no release found for $projectRef"))
          // compute stuff
          allVersions = releases.map(_.version)
          filteredVersions = filterVersions(project, allVersions)
          targets = releases.flatMap(_.target).distinct.sorted.reverse
          artifactNames = releases.map(_.artifactName).distinct
        } yield (
          StatusCodes.OK,
          views.project.html.project(
            project,
            artifactNames,
            filteredVersions,
            targets,
            selectedRelease,
            Dependencies.empty(selectedRelease.reference),
            user,
            canEdit = true,
            None
          )
        )
      case None =>
        Future.successful((StatusCodes.NotFound, views.html.notfound(user)))
    }
  }

  private val moved = GithubReader.movedRepositories(paths)

  private def redirectMoved(
      organization: NewProject.Organization,
      repository: NewProject.Repository
  ): Directive0 =
    moved.get(GithubRepo(organization.value, repository.value)) match {
      case Some(destination) =>
        redirect(
          Uri(s"/${destination.organization}/${destination.repository}"),
          StatusCodes.PermanentRedirect
        )
      case None => pass
    }

  val routes: Route =
    concat(
      post(
        path("edit" / Segment / Segment)((organization, repository) =>
          optionalSession(refreshable, usingCookies)(_ =>
            pathEnd(
              editForm { form =>
                val ref = Project.Reference(organization, repository)
                val updated = for {
                  _ <- localStorage.saveDataForm(ref, form)
                  updated <- db.updateProjectForm(
                    ref,
                    form.toUserDataForm()
                  )
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
            val user = session.getUser(userId).map(_.info)
            val ref = Project.Reference(org.value, repo.value)
            val res =
              for {
                projectOpt <- db.findProject(ref)
                project <- projectOpt.toFuture(
                  new Exception(s"project ${ref} not found")
                )
                releases <- db.findReleases(project.reference)
                // some computation
                targetTypesWithScalaVersion = releases
                  .groupBy(_.target.map(_.targetType).getOrElse(Java))
                  .map { case (targetType, releases) =>
                    (
                      targetType,
                      releases.map(_.scalaVersion).distinct.sorted.reverse
                    )
                  }
                artifactsWithVersions = releases
                  .groupBy(_.version)
                  .map { case (semanticVersion, releases) =>
                    (
                      semanticVersion,
                      releases.groupBy(_.artifactName).map {
                        case (artifactName, releases) =>
                          (artifactName, releases.map(r => (r, r.scalaVersion)))
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
                  views.html.artifacts(
                    project,
                    user,
                    targetTypesWithScalaVersion,
                    artifactsWithVersions
                  )
                )
              case Failure(e) =>
                complete(StatusCodes.NotFound, views.html.notfound(user))

            }
          }
        )
      },
      get {
        path("edit" / organizationM / repositoryM)((organization, repository) =>
          optionalSession(refreshable, usingCookies)(userId =>
            pathEnd {
              val projectRef =
                Project.Reference(organization.value, repository.value)
              session.getUser(userId) match {
                case Some(userState)
                    if userState.canEdit(projectRef.githubRepo) =>
                  complete(getEditPage(projectRef, userState.info))
                case maybeUser =>
                  complete(
                    (
                      StatusCodes.Forbidden,
                      views.html.forbidden(maybeUser.map(_.info))
                    )
                  )
              }
            }
          )
        )
      },
      get {
        path(organizationM / repositoryM)((organization, repository) =>
          redirectMoved(organization, repository)(
            optionalSession(refreshable, usingCookies)(userId =>
              parameters(
                ("artifact".?, "version".?, "target".?, "selected".?)
              )((artifact, version, target, selected) =>
                onSuccess(
                  getSelectedRelease(
                    org = organization,
                    repo = repository,
                    target = target,
                    artifact = artifact,
                    version = version,
                    selected = selected
                  )
                ) {
                  case Some(release) =>
                    val targetParam =
                      release.target match {
                        case Some(target) =>
                          s"?target=${target.encode}"
                        case None => ""
                      }
                    redirect(
                      s"/$organization/$repository/${release.reference.artifact}/${release.reference.version}/$targetParam",
                      StatusCodes.TemporaryRedirect
                    )
                  case None =>
                    complete(
                      StatusCodes.NotFound,
                      views.html.notfound(
                        session.getUser(userId).map(_.info)
                      )
                    )
                }
              )
            )
          )
        )
      },
      get {
        path(organizationM / repositoryM / artifactM)(
          (organization, repository, artifact) =>
            optionalSession(refreshable, usingCookies)(userId =>
              parameter("target".?) { target =>
                val user = session.getUser(userId).map(_.info)
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
                    complete(StatusCodes.NotFound, views.html.notfound(user))
                }
              }
            )
        )
      },
      get {
        path(organizationM / repositoryM / artifactM / versionM)(
          (organization, repository, artifact, version) =>
            optionalSession(refreshable, usingCookies) { userId =>
              parameter("target".?) { target =>
                val user = session.getUser(userId).map(_.info)
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
                    complete(StatusCodes.NotFound, views.html.notfound(user))
                }
              }
            }
        )
      }
    )
}
