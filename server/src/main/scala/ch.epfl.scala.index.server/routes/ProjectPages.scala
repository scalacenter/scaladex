package ch.epfl.scala.index
package server
package routes

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import ch.epfl.scala.index.data.DataPaths
import ch.epfl.scala.index.data.github.{GithubReader, Json4s}
import ch.epfl.scala.index.data.project.ProjectForm
import ch.epfl.scala.index.model._
import ch.epfl.scala.index.model.misc._
import ch.epfl.scala.index.model.release._
import ch.epfl.scala.index.server.TwirlSupport._
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import org.json4s.native.Serialization.{read, write}

import scala.concurrent.{ExecutionContext, Future}

class ProjectPages(
    dataRepository: DataRepository,
    session: GithubUserSession,
    paths: DataPaths
)(implicit executionContext: ExecutionContext) {
  import session.implicits._

  private def canEdit(owner: String,
                      repo: String,
                      userState: Option[UserState]) = {
    userState.exists(
      s => s.isAdmin || s.repos.contains(GithubRepo(owner, repo))
    )
  }

  private def getEditPage(owner: String,
                          repo: String,
                          userState: Option[UserState]) = {
    val user = userState.map(_.user)
    if (canEdit(owner, repo, userState)) {
      for {
        project <- dataRepository.getProject(Project.Reference(owner, repo))
      } yield {
        project
          .map { p =>
            val beginnerIssuesJson = p.github
              .map { github =>
                import Json4s._
                write[List[GithubIssue]](github.beginnerIssues)
              }
              .getOrElse("")
            (OK, views.project.html.editproject(p, user, beginnerIssuesJson))
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
      .getProjectPage(Project.Reference(owner, repo), selection)
      .map(_.map { case (_, options) => options.release })
  }

  private def artifactsPage(owner: String,
                            repo: String,
                            userState: Option[UserState]) = {

    val user = userState.map(_.user)

    def showTarget(target: ScalaTarget): String = {
      target match {
        case ScalaTarget(scalaVersion, None, None, None) =>
          scalaVersion.toString

        case ScalaTarget(scalaVersion, Some(scalaJsVersion), None, None) =>
          scalaVersion.toString

        case ScalaTarget(scalaVersion, None, Some(scalaNativeVersion), None) =>
          scalaNativeVersion.toString

        case ScalaTarget(scalaVersion, None, None, Some(sbtVersion)) =>
          sbtVersion.toString

        case _ => "" // impossible
      }
    }

    dataRepository
      .getProjectAndReleases(Project.Reference(owner, repo))
      .map {
        case Some((project, releases)) => {
          val targets =
            releases
              .map(_.reference.target)
              .sorted
              .reverse
              .map(
                target =>
                  (
                    target,
                    target.map(showTarget).getOrElse("Java")
                )
              )
              .distinct

          val targetTypes =
            targets
              .map(
                _._1
                  .map(_.targetType)
                  .getOrElse(Java)
              )
              .groupBy(x => x)
              .map { case (k, vs) => (k, vs.size) }
              .toList

          val sortedReleases =
            releases
              .groupBy(_.reference.version)
              .toList
              .sortBy(_._1)
              .reverse

          (OK,
           views.html
             .artifacts(project, sortedReleases, targetTypes, targets, user))
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
      .getProjectPage(Project.Reference(owner, repo), selection)
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

          val versions0 =
            if (project.strictVersions) versions.filter(_.isSemantic)
            else versions

          (OK,
           views.project.html.project(
             project,
             options.artifacts,
             versions0,
             targets,
             release,
             user,
             canEdit(owner, repo, userState),
             twitterCard
           ))
      }.getOrElse((NotFound, views.html.notfound(user))))
  }

  private val moved = GithubReader.movedRepositories(paths)

  private def redirectMoved(organization: String,
                            repository: String): Directive0 = {
    moved.get(GithubRepo(organization, repository)) match {
      case Some(destination) =>
        redirect(
          Uri(s"/${destination.organization}/${destination.repository}"),
          PermanentRedirect
        )

      case None => pass
    }
  }

  val editForm: Directive1[ProjectForm] =
    formFieldSeq.tflatMap(
      fields =>
        formFields(
          (
            'contributorsWanted.as[Boolean] ? false,
            'defaultArtifact.?,
            'defaultStableVersion.as[Boolean] ? false,
            'strictVersions.as[Boolean] ? false,
            'deprecated.as[Boolean] ? false,
            'artifactDeprecations.*,
            'cliArtifacts.*,
            'customScalaDoc.?,
            'primaryTopic.?,
            'beginnerIssuesLabel.?,
            'beginnerIssues.?,
            'selectedBeginnerIssues.*,
            'chatroom.?,
            'contributingGuide.?,
            'codeOfConduct.?
          )
        ).tmap {
          case (contributorsWanted,
                defaultArtifact,
                defaultStableVersion,
                strictVersions,
                deprecated,
                artifactDeprecations,
                cliArtifacts,
                customScalaDoc,
                primaryTopic,
                beginnerIssuesLabel,
                beginnerIssues,
                selectedBeginnerIssues,
                chatroom,
                contributingGuide,
                codeOfConduct) =>
            val documentationLinks = {
              val name = "documentationLinks"
              val end = "]".head

              fields._1
                .filter { case (key, _) => key.startsWith(name) }
                .groupBy {
                  case (key, _) =>
                    key
                      .drop("documentationLinks[".length)
                      .takeWhile(_ != end)
                }
                .values
                .map {
                  case Vector((a, b), (_, d)) =>
                    if (a.contains("label")) (b, d)
                    else (d, b)
                }
                .toList
            }

            val keywords = Set[String]()

            import Json4s._
            ProjectForm(
              contributorsWanted,
              keywords,
              defaultArtifact,
              defaultStableVersion,
              strictVersions,
              deprecated,
              artifactDeprecations.toSet,
              cliArtifacts.toSet,
              customScalaDoc,
              documentationLinks,
              primaryTopic,
              beginnerIssuesLabel,
              beginnerIssues.map(read[List[GithubIssue]](_)).getOrElse(List()),
              selectedBeginnerIssues.map(read[GithubIssue](_)).toList,
              chatroom.map(Url),
              contributingGuide.map(Url),
              codeOfConduct.map(Url)
            )
      }
    )

  val routes: Route =
    concat(
      post(
        path("edit" / Segment / Segment)(
          (organization, repository) =>
            optionalSession(refreshable, usingCookies)(
              _ =>
                pathEnd(
                  editForm(
                    form =>
                      onSuccess(
                        dataRepository.updateProject(
                          Project.Reference(organization, repository),
                          form
                        )
                      ) { _ =>
                        Thread.sleep(1000) // oh yeah
                        redirect(Uri(s"/$organization/$repository"), SeeOther)
                    }
                  )
              )
          )
        )
      ),
      get(
        concat(
          path("artifacts" / Segment / Segment)(
            (organization, repository) =>
              optionalSession(refreshable, usingCookies)(
                userId =>
                  pathEnd(
                    complete(
                      artifactsPage(organization,
                                    repository,
                                    session.getUser(userId))
                    )
                )
            )
          ),
          path("edit" / Segment / Segment)(
            (organization, repository) =>
              optionalSession(refreshable, usingCookies)(
                userId =>
                  pathEnd(
                    complete(
                      getEditPage(organization,
                                  repository,
                                  session.getUser(userId))
                    )
                )
            )
          ),
          path(Segment / Segment)(
            (organization, repository) =>
              redirectMoved(organization, repository)(
                optionalSession(refreshable, usingCookies)(
                  userId =>
                    parameters(
                      ('artifact.?, 'version.?, 'target.?, 'selected.?)
                    )(
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
                        ) {
                          case Some(release) =>
                            val targetParam =
                              release.reference.target match {
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
                              ((NotFound,
                                views.html
                                  .notfound(
                                    session.getUser(userId).map(_.user)
                                  )))
                            )
                      }
                  )
                )
            )
          ),
          path(Segment / Segment / Segment)(
            (organization, repository, artifact) =>
              optionalSession(refreshable, usingCookies)(
                userId =>
                  parameter('target.?)(
                    target =>
                      complete(
                        projectPage(
                          owner = organization,
                          repo = repository,
                          target = target,
                          artifact = Some(artifact),
                          version = None,
                          selected = None,
                          userState = session.getUser(userId)
                        )
                    )
                )
            )
          ),
          path(Segment / Segment / Segment / Segment)(
            (organization, repository, artifact, version) =>
              optionalSession(refreshable, usingCookies)(
                userId =>
                  parameter('target.?)(
                    target =>
                      complete(
                        projectPage(
                          owner = organization,
                          repo = repository,
                          target = target,
                          artifact = Some(artifact),
                          version = Some(version),
                          selected = None,
                          userState = session.getUser(userId)
                        )
                    )
                )
            )
          )
        )
      )
    )
}
