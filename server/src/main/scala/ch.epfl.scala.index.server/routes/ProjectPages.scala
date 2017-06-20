package ch.epfl.scala.index
package server
package routes

import model._
import release._
import data.project.ProjectForm
import model.misc._
import com.softwaremill.session._
import SessionDirectives._
import SessionOptions._
import TwirlSupport._
import akka.http.scaladsl._
import model._
import server.Directives._
import Uri._
import StatusCodes._
import org.slf4j.LoggerFactory

import scala.concurrent.Future

class ProjectPages(dataRepository: DataRepository, session: GithubUserSession) {
  import session._

  val logger = LoggerFactory.getLogger(this.getClass)

  private def canEdit(owner: String,
                      repo: String,
                      userState: Option[UserState]) =
    userState
      .map(s => s.isAdmin || s.repos.contains(GithubRepo(owner, repo)))
      .getOrElse(false)

  private def editPage(owner: String,
                       repo: String,
                       userState: Option[UserState]) = {
    val user = userState.map(_.user)
    if (canEdit(owner, repo, userState)) {
      for {
        project <- dataRepository.project(Project.Reference(owner, repo))
      } yield {
        project
          .map { p =>
            (OK, views.project.html.editproject(p, user))
          }
          .getOrElse((NotFound, views.html.notfound(user)))
      }
    } else Future.successful((Forbidden, views.html.forbidden(user)))
  }

  private def projectPage(owner: String,
                          repo: String,
                          target: Option[String],
                          artifact: Option[String],
                          version: Option[String],
                          userState: Option[UserState]) = {

    val user = userState.map(_.user)

    val selection = ReleaseSelection.parse(
      target = target,
      artifactName = artifact,
      version = version
    )

    dataRepository
      .projectPage(Project.Reference(owner, repo), selection)
      .map(_.map {
        case (project, options) =>
          import options._
          val twitterCard = for {
            github <- project.github
            description <- github.description
          } yield
            TwitterSummaryCard(
              site = "@scala_lang",
              title = s"${project.organization}/${project.repository}",
              description = description,
              image = github.logo
            )

          (OK,
           views.project.html.project(
             project,
             options.artifacts,
             versions,
             targets,
             release,
             target,
             user,
             canEdit(owner, repo, userState),
             twitterCard
           ))
      }.getOrElse((NotFound, views.html.notfound(user))))
  }

  val routes =
    post {
      path("edit" / Segment / Segment) { (organization, repository) =>
        logger.info(s"Saving data of $organization/$repository")
        optionalSession(refreshable, usingCookies) { userId =>
          pathEnd {
            formFieldSeq { fields =>
              formFields((
                'contributorsWanted.as[Boolean] ? false,
                'defaultArtifact.?,
                'defaultStableVersion.as[Boolean] ? false,
                'deprecated.as[Boolean] ? false,
                'artifactDeprecations.*,
                'cliArtifacts.*,
                'customScalaDoc.?
              )) {
                (contributorsWanted,
                 defaultArtifact,
                 defaultStableVersion,
                 deprecated,
                 artifactDeprecations,
                 cliArtifacts,
                 customScalaDoc) =>
                  val documentationLinks = {
                    val name = "documentationLinks"
                    val end = "]".head

                    fields
                      .filter { case (key, _) => key.startsWith(name) }
                      .groupBy {
                        case (key, _) =>
                          key
                            .drop("documentationLinks[".length)
                            .takeWhile(_ != end)
                      }
                      .values
                      .map {
                        case Vector((a, b), (c, d)) =>
                          if (a.contains("label")) (b, d)
                          else (d, b)
                      }
                      .toList
                  }

                  onSuccess(
                    dataRepository.updateProject(
                      Project.Reference(organization, repository),
                      ProjectForm(
                        contributorsWanted,
                        defaultArtifact,
                        defaultStableVersion,
                        deprecated,
                        artifactDeprecations.toSet,
                        cliArtifacts.toSet,
                        // documentation
                        customScalaDoc,
                        documentationLinks
                      )
                    )
                  ) { ret =>
                    Thread.sleep(1000) // oh yeah
                    redirect(Uri(s"/$organization/$repository"), SeeOther)
                  }
              }
            }
          }
        }
      }
    } ~
      get {
        path("edit" / Segment / Segment) { (organization, repository) =>
          optionalSession(refreshable, usingCookies) { userId =>
            pathEnd {
              complete(editPage(organization, repository, getUser(userId)))
            }
          }
        } ~
          path(Segment / Segment) { (organization, repository) =>
            optionalSession(refreshable, usingCookies) { userId =>
              parameters(('artifact.?, 'version.?, 'target.?)) {
                (artifact, version, target) =>
                  val rest = (artifact, version) match {
                    case (Some(a), Some(v)) => s"$a/$v"
                    case (Some(a), None)    => a
                    case _                  => ""
                  }
                  val targetQuery = target match {
                    case Some(t) => s"?target=$t"
                    case _       => ""
                  }
                  if (artifact.isEmpty && version.isEmpty) {
                    complete(
                      projectPage(organization,
                                  repository,
                                  target,
                                  None,
                                  None,
                                  getUser(userId)))
                  } else {
                    redirect(s"/$organization/$repository/$rest$targetQuery",
                             StatusCodes.PermanentRedirect)
                  }
              }
            }
          } ~
          path(Segment / Segment / Segment) {
            (organization, repository, artifact) =>
              optionalSession(refreshable, usingCookies) { userId =>
                parameter('target.?) { target =>
                  complete(
                    projectPage(organization,
                                repository,
                                target,
                                Some(artifact),
                                None,
                                getUser(userId))
                  )
                }
              }
          } ~
          path(Segment / Segment / Segment / Segment) {
            (organization, repository, artifact, version) =>
              optionalSession(refreshable, usingCookies) { userId =>
                parameter('target.?) { target =>
                  complete(
                    projectPage(organization,
                                repository,
                                target,
                                Some(artifact),
                                Some(version),
                                getUser(userId))
                  )
                }
              }
          }
      }
}
