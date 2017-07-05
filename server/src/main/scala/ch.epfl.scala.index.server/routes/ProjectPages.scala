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

  private def redirectTo(owner: String,
                         repo: String,
                         target: Option[String],
                         artifact: Option[String],
                         version: Option[String],
                         selected: Option[String]): Future[Option[Release]] = {

    val selection = ReleaseSelection.parse(
      target = target,
      artifactName = artifact,
      version = version,
      selected = selected
    )
    
    dataRepository
      .projectPage(Project.Reference(owner, repo), selection)
      .map(_.map {case (_, options) => options.release})
  }

  private def artifactsPage(owner: String,
                            repo: String,
                            userState: Option[UserState]) = {

    val user = userState.map(_.user)

    def showTarget(target: ScalaTarget): String = {
      target match {
        case ScalaTarget(scalaVersion, None, None) => 
          scalaVersion.toString

        case ScalaTarget(scalaVersion, Some(scalaJsVersion), None) =>
          scalaVersion.toString

        case ScalaTarget(scalaVersion, None, Some(scalaNativeVersion)) =>
          scalaNativeVersion.toString

        case _ => "" // impossible
      }
    }

    dataRepository.projectAndReleases(Project.Reference(owner, repo), ReleaseSelection.empty).map{
      case Some((project, releases)) => {
        val targets =
          releases
            .map(_.reference.target)
            .sorted
            .reverse
            .map(target =>
              (
                target,
                target.map(showTarget).getOrElse("Java")
              )
            )
            .distinct

        val targetTypes =
          targets
            .map(_._1.map(_.targetType)
            .getOrElse(Java))
            .groupBy(x => x)
            .map{case (k, vs) => (k, vs.size)}
            .toList

        val sortedReleases = 
          releases
            .groupBy(_.reference.version)
            .toList
            .sortBy(_._1)
            .reverse

        (OK, views.html.artifacts(project, sortedReleases, targetTypes, targets, user))
      }
      case None => (NotFound, views.html.notfound(user))
    }
  }

  private def projectPage(owner: String,
                          repo: String,
                          target: Option[String],
                          artifact: Option[String],
                          version: Option[String],
                          selected: Option[String],
                          userState: Option[UserState]) = {

    val user = userState.map(_.user)

    val selection = ReleaseSelection.parse(
      target = target,
      artifactName = artifact,
      version = version,
      selected = selected
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
              formFields(
                (
                  'contributorsWanted.as[Boolean] ? false,
                  'defaultArtifact.?,
                  'defaultStableVersion.as[Boolean] ? false,
                  'deprecated.as[Boolean] ? false,
                  'artifactDeprecations.*,
                  'cliArtifacts.*,
                  'customScalaDoc.?,
                  'primaryTopic.?
                )) {
                (contributorsWanted,
                 defaultArtifact,
                 defaultStableVersion,
                 deprecated,
                 artifactDeprecations,
                 cliArtifacts,
                 customScalaDoc,
                 primaryTopic) =>
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

                  val keywords = Set[String]()

                  onSuccess(
                    dataRepository.updateProject(
                      Project.Reference(organization, repository),
                      ProjectForm(
                        contributorsWanted,
                        keywords,
                        defaultArtifact,
                        defaultStableVersion,
                        deprecated,
                        artifactDeprecations.toSet,
                        cliArtifacts.toSet,
                        customScalaDoc,
                        documentationLinks,
                        primaryTopic
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
      get(
        concat(
          path("artifacts" / Segment / Segment)((organization, repository) =>
            optionalSession(refreshable, usingCookies)(userId =>
              pathEnd(
                complete(artifactsPage(organization, repository, getUser(userId)))
              )
            )
          ),
          path("edit" / Segment / Segment)((organization, repository) =>
            optionalSession(refreshable, usingCookies)(userId =>
              pathEnd(
                complete(editPage(organization, repository, getUser(userId)))
              )
            )
          ),
          path(Segment / Segment)((organization, repository) =>
            optionalSession(refreshable, usingCookies)(userId =>
              parameters(('artifact.?, 'version.?, 'target.?, 'selected.?))(
                (artifact, version, target, selected) =>

                  onSuccess(
                    redirectTo(
                      owner = organization,
                      repo = repository,
                      target = target,
                      artifact = artifact,
                      version = version,
                      selected = selected
                    )
                  )(maybeRelease =>
                    maybeRelease match {
                      case Some(release) => {
                        val targetParam =
                          release.reference.target match {
                            case Some(target) => s"?target=${target.encode}"
                            case None => ""
                          }

                        redirect(
                          s"/$organization/$repository/${release.reference.artifact}/${release.reference.version}/$targetParam",
                          StatusCodes.TemporaryRedirect
                        )
                      }
                      case None => complete(((NotFound, views.html.notfound(getUser(userId).map(_.user)))))
                    }
                  )
              )
            )
          ),
          path(Segment / Segment / Segment)(
            (organization, repository, artifact) =>
              optionalSession(refreshable, usingCookies) ( userId =>
                parameter('target.?) ( target =>
                  complete(
                    projectPage(
                      owner = organization,
                      repo = repository,
                      target = target,
                      artifact = Some(artifact),
                      version = None,
                      selected = None,
                      userState = getUser(userId)
                    )
                  )
                )
              )
          ),
          path(Segment / Segment / Segment / Segment)(
            (organization, repository, artifact, version) =>
              optionalSession(refreshable, usingCookies)(userId =>
                parameter('target.?) (target =>
                  complete(
                    projectPage(
                      owner = organization,
                      repo = repository,
                      target = target,
                      artifact = Some(artifact),
                      version = Some(version),
                      selected = None,
                      userState = getUser(userId)
                    )
                  )
                )
              )
          )
        )
      )
}
