package ch.epfl.scala.index
package server
package routes

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import ch.epfl.scala.index.data.DataPaths
import ch.epfl.scala.index.data.elastic.SaveLiveData
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.github.GithubReader
import ch.epfl.scala.index.data.github.Json4s
import ch.epfl.scala.index.data.project.ProjectForm
import ch.epfl.scala.index.model._
import ch.epfl.scala.index.model.misc._
import ch.epfl.scala.index.model.release._
import ch.epfl.scala.index.search.ESRepo
import ch.epfl.scala.index.server.TwirlSupport._
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import com.typesafe.scalalogging.LazyLogging
import org.json4s.native.Serialization.read
import org.json4s.native.Serialization.write

class ProjectPages(
    dataRepository: ESRepo,
    session: GithubUserSession,
    githubDownload: GithubDownload,
    paths: DataPaths
)(implicit executionContext: ExecutionContext)
    extends LazyLogging {
  import session.implicits._

  private def canEdit(
      owner: String,
      repo: String,
      userState: Option[UserState]
  ): Boolean = {
    userState.exists(s =>
      s.isAdmin || s.repos.contains(GithubRepo(owner, repo))
    )
  }

  private def getEditPage(
      owner: String,
      repo: String,
      userState: Option[UserState]
  ) = {
    val user = userState.map(_.info)
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

  private def getSelectedRelease(
      owner: String,
      repo: String,
      target: Option[String],
      artifact: Option[String],
      version: Option[String],
      selected: Option[String]
  ): Future[Option[Release]] = {

    dataRepository
      .getProjectAndReleaseOptions(
        Project.Reference(owner, repo),
        ReleaseSelection.parse(
          target = target,
          artifactName = artifact,
          version = version,
          selected = selected
        )
      )
      .map(_.map { case (_, options) => options.release })
  }

  private def artifactsPage(
      owner: String,
      repo: String,
      userState: Option[UserState]
  ) = {
    type ArtifactName = String
    type ScalaVersion = String

    val user = userState.map(_.info)

    def showVersion(target: ScalaTarget): String = {
      target match {
        case ScalaJvm(version) => version.toString
        case ScalaJs(version, jsVersion) => s"${jsVersion}_$version"
        case ScalaNative(version, nativeVersion) => s"${nativeVersion}_$version"
        case SbtPlugin(version, sbtVersion) => s"${sbtVersion}_$version"
      }
    }

    dataRepository
      .getProjectAndReleases(Project.Reference(owner, repo))
      .map {
        case Some((project, releases)) =>
          val targetTypesWithScalaVersion
              : Map[ScalaTargetType, Seq[ScalaVersion]] =
            releases
              .groupBy(_.reference.target.map(_.targetType).getOrElse(Java))
              .map { case (targetType, releases) =>
                (
                  targetType,
                  releases
                    .map(
                      _.reference.target.map(showVersion).getOrElse("Java")
                    )
                    .distinct
                    .sorted
                    .reverse
                )
              }

          val artifactsWithVersions: Seq[
            (SemanticVersion, Map[ArtifactName, Seq[(Release, ScalaVersion)]])
          ] = {
            releases
              .groupBy(_.reference.version)
              .map { case (semanticVersion, releases) =>
                (
                  semanticVersion,
                  releases
                    .groupBy(_.reference.artifact)
                    .map { case (artifactName, releases) =>
                      (
                        artifactName,
                        releases.map(r =>
                          (
                            r,
                            r.reference.target
                              .map(showVersion)
                              .getOrElse("Java")
                          )
                        )
                      )
                    }
                )
              }
              .toSeq
              .sortBy(_._1)
              .reverse
          }

          (
            OK,
            views.html
              .artifacts(
                project,
                user,
                targetTypesWithScalaVersion,
                artifactsWithVersions
              )
          )
        case None => (NotFound, views.html.notfound(user))
      }
  }

  private def projectPage(
      owner: String,
      repo: String,
      target: Option[String],
      artifact: Option[String],
      version: Option[String],
      selected: Option[String],
      userState: Option[UserState]
  ) = {

    val user = userState.map(_.info)

    val selection = ReleaseSelection.parse(
      target = target,
      artifactName = artifact,
      version = version,
      selected = selected
    )
    val projectRef = Project.Reference(owner, repo)

    dataRepository
      .getProjectAndReleaseOptions(projectRef, selection)
      .flatMap {
        case Some((project, options)) =>
          val releaseRef = options.release.reference
          val dependenciesF = dataRepository.getAllDependencies(releaseRef)
          val reverseDependenciesF =
            dataRepository.getReverseDependencies(releaseRef)

          for {
            dependencies <- dependenciesF
            reverseDependencies <- reverseDependenciesF
          } yield {
            val allDeps =
              Dependencies(options.release, dependencies, reverseDependencies)

            val versions =
              if (project.strictVersions) options.versions.filter(_.isSemantic)
              else options.versions

            val twitterCard = for {
              github <- project.github
              description <- github.description
            } yield TwitterSummaryCard(
              site = "@scala_lang",
              title = s"${project.organization}/${project.repository}",
              description = description,
              image = github.logo
            )

            val page = views.project.html.project(
              project,
              options.artifacts,
              versions,
              options.targets,
              options.release,
              allDeps,
              user,
              canEdit(owner, repo, userState),
              twitterCard
            )

            (OK, page)
          }

        case None => Future.successful(NotFound, views.html.notfound(user))
      }
  }

  private val moved = GithubReader.movedRepositories(paths)

  private def redirectMoved(
      organization: String,
      repository: String
  ): Directive0 = {
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
    formFieldSeq.tflatMap(fields =>
      formFields(
        (
          "contributorsWanted".as[Boolean] ? false,
          "defaultArtifact".?,
          "defaultStableVersion".as[Boolean] ? false,
          "strictVersions".as[Boolean] ? false,
          "deprecated".as[Boolean] ? false,
          "artifactDeprecations".as[String].*,
          "cliArtifacts".as[String].*,
          "customScalaDoc".?,
          "primaryTopic".?,
          "beginnerIssuesLabel".?,
          "beginnerIssues".?,
          "selectedBeginnerIssues".as[String].*,
          "chatroom".?,
          "contributingGuide".?,
          "codeOfConduct".?
        )
      ).tmap {
        case (
              contributorsWanted,
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
              codeOfConduct
            ) =>
          val documentationLinks = {
            val name = "documentationLinks"
            val end = "]".head

            fields._1
              .filter { case (key, _) => key.startsWith(name) }
              .groupBy { case (key, _) =>
                key
                  .drop("documentationLinks[".length)
                  .takeWhile(_ != end)
              }
              .values
              .map { case Vector((a, b), (_, d)) =>
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

  def updateProject(
      projectRef: Project.Reference,
      form: ProjectForm
  ): Future[Boolean] = {
    for {
      projectOpt <- dataRepository.getProject(projectRef)
      updated <- projectOpt match {
        case Some(project) if project.id.isDefined =>
          val updatedProject = form.update(project, paths, githubDownload)
          val esUpdate = dataRepository.updateProject(updatedProject)

          logger.info("Updating live data on the index repository")
          val indexUpdate = SaveLiveData.saveProject(updatedProject, paths)

          esUpdate.zip(indexUpdate).map(_ => true)
        case _ => Future.successful(false)
      }
    } yield updated
  }

  val routes: Route =
    concat(
      post(
        path("edit" / Segment / Segment)((organization, repository) =>
          optionalSession(refreshable, usingCookies)(_ =>
            pathEnd(
              editForm(form =>
                onSuccess {
                  updateProject(
                    Project.Reference(organization, repository),
                    form
                  )
                } { _ =>
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
          path("artifacts" / Segment / Segment)((organization, repository) =>
            optionalSession(refreshable, usingCookies)(userId =>
              pathEnd(
                complete(
                  artifactsPage(
                    organization,
                    repository,
                    session.getUser(userId)
                  )
                )
              )
            )
          ),
          path("edit" / Segment / Segment)((organization, repository) =>
            optionalSession(refreshable, usingCookies)(userId =>
              pathEnd(
                complete(
                  getEditPage(organization, repository, session.getUser(userId))
                )
              )
            )
          ),
          path(Segment / Segment)((organization, repository) =>
            redirectMoved(organization, repository)(
              optionalSession(refreshable, usingCookies)(userId =>
                parameters(
                  ("artifact".?, "version".?, "target".?, "selected".?)
                )((artifact, version, target, selected) =>
                  onSuccess(
                    getSelectedRelease(
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
                        NotFound,
                        views.html.notfound(
                          session.getUser(userId).map(_.info)
                        )
                      )
                  }
                )
              )
            )
          ),
          path(Segment / Segment / Segment)(
            (organization, repository, artifact) =>
              optionalSession(refreshable, usingCookies)(userId =>
                parameter("target".?)(target =>
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
              optionalSession(refreshable, usingCookies)(userId =>
                parameter("target".?)(target =>
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
