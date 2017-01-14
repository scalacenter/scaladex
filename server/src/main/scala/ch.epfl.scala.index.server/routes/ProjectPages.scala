package ch.epfl.scala.index
package server
package routes

import model._
import data.project.ProjectForm
import release._
import model.misc._

import com.softwaremill.session._, SessionDirectives._, SessionOptions._

import TwirlSupport._

import akka.http.scaladsl._
import model._
import server.Directives._
import Uri._
import StatusCodes._

import scala.concurrent.Future

class ProjectPages(dataRepository: DataRepository, session: GithubUserSession) {
  import session._

  private def canEdit(owner: String, repo: String, userState: Option[UserState]) =
    userState.map(s => s.isAdmin || s.repos.contains(GithubRepo(owner, repo))).getOrElse(false)

  private def editPage(owner: String, repo: String, userState: Option[UserState]) = {
    val user = userState.map(_.user)
    if (canEdit(owner, repo, userState)) {
      for {
        keywords <- dataRepository.keywords(None)
        project <- dataRepository.project(Project.Reference(owner, repo))
      } yield {
        project.map { p =>
          val allKeywords = (p.keywords ++ keywords.keys.toSet).toList.sorted
          (OK, views.project.html.editproject(p, allKeywords, user))
        }.getOrElse((NotFound, views.html.notfound(user)))
      }
    } else Future.successful((Forbidden, views.html.forbidden(user)))
  }

  private def projectPage(owner: String,
                          repo: String,
                          artifact: Option[String],
                          version: Option[SemanticVersion],
                          userState: Option[UserState],
                          statusCode: StatusCode = OK) = {

    val user = userState.map(_.user)

    dataRepository
      .project(Project.Reference(owner, repo))
      .map(_.map(project =>
        (statusCode,
          views.project.html.project(
            project,
            user,
            canEdit(owner, repo, userState)
          ))
      ).getOrElse((NotFound, views.html.notfound(user))))
  }

  private def artifactPage(owner: String,
                           repo: String,
                           artifact: String,
                           version: Option[SemanticVersion],
                           userState: Option[UserState],
                           statusCode: StatusCode = OK) = {

    val user = userState.map(_.user)

    dataRepository
      .artifactPage(Project.Reference(owner, repo), ReleaseSelection(Some(artifact), version))
      .map(_.map {
        case (project, releases, release) =>
          (statusCode,
            views.artifact.html.artifact(
              project,
              releases,
              release,
              user,
              canEdit(owner, repo, userState)
            ))
      }.getOrElse((NotFound, views.html.notfound(user))))
  }

  val routes =
    post {
      path("edit" / Segment / Segment) { (organization, repository) =>
        optionalSession(refreshable, usingCookies) { userId =>
          pathEnd {
            formFieldSeq { fields =>
              formFields(
                'contributorsWanted.as[Boolean] ? false,
                'keywords.*,
                'defaultArtifact.?,
                'defaultStableVersion.as[Boolean] ? false,
                'deprecated.as[Boolean] ? false,
                'artifactDeprecations.*,
                'cliArtifacts.*,
                'customScalaDoc.?
              ) {
                (contributorsWanted, keywords, defaultArtifact, defaultStableVersion, deprecated,
                 artifactDeprecations, cliArtifacts, customScalaDoc) =>
                  val documentationLinks = {
                    val name = "documentationLinks"
                    val end = "]".head

                    fields.filter { case (key, _) => key.startsWith(name) }.groupBy {
                      case (key, _) => key.drop("documentationLinks[".length).takeWhile(_ != end)
                    }.values.map {
                      case Vector((a, b), (c, d)) =>
                        if (a.contains("label")) (b, d)
                        else (d, b)
                    }.toList
                  }

                  onSuccess(
                    dataRepository.updateProject(
                      Project.Reference(organization, repository),
                      ProjectForm(
                        contributorsWanted,
                        keywords.toSet,
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
              parameters('artifact, 'version.?) { (artifact, version) =>
                val rest = version match {
                  case Some(v) if !v.isEmpty => "/" + v
                  case _ => ""
                }
                redirect(s"/$organization/$repository/$artifact$rest",
                         StatusCodes.PermanentRedirect)
              } ~
                pathEnd {
                  complete(projectPage(organization, repository, None, None, getUser(userId)))
                }
            }
          } ~
          path(Segment / Segment / Segment) { (organization, repository, artifact) =>
            optionalSession(refreshable, usingCookies) { userId =>
              complete(
                artifactPage(organization, repository, artifact, None, getUser(userId)))
            }
          } ~
          path(Segment / Segment / Segment / Segment) {
            (organization, repository, artifact, version) =>
              optionalSession(refreshable, usingCookies) { userId =>
                complete(
                  artifactPage(organization,
                              repository,
                              artifact,
                              SemanticVersion(version),
                              getUser(userId)))
              }
          }
      }
}
