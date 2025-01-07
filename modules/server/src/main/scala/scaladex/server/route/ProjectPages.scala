package scaladex.server.route
import java.time.Instant
import java.util.regex.Matcher
import java.util.regex.Pattern

import scala.collection.SortedMap
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import scaladex.core.model.*
import scaladex.core.service.ProjectService
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.SearchEngine
import scaladex.core.web.ArtifactPageParams
import scaladex.core.web.ArtifactsPageParams
import scaladex.server.TwirlSupport.given
import scaladex.server.service.ArtifactService
import scaladex.server.service.SearchSynchronizer
import scaladex.view.html.forbidden
import scaladex.view.html.notfound
import scaladex.view.project.html

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.http.scaladsl.model.Uri.*
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.*

class ProjectPages(
    env: Env,
    projectService: ProjectService,
    artifactService: ArtifactService,
    database: SchedulerDatabase,
    searchEngine: SearchEngine
)(
    using ExecutionContext
) extends LazyLogging:

  private val searchSynchronizer = new SearchSynchronizer(database, projectService, searchEngine)

  def route(user: Option[UserState]): Route =
    concat(
      routes = get {
        path(projectM)(getProjectPage(_, user))
      },
      get {
        path(projectM / "artifacts") { ref =>
          artifactsParams { params =>
            getProjectOrRedirect(ref, user) { project =>
              for header <- projectService.getHeader(project) yield
                val allArtifacts = header.toSeq.flatMap(_.artifacts)

                val binaryVersions = allArtifacts
                  .map(_.binaryVersion)
                  .distinct
                  .sorted(BinaryVersion.ordering.reverse)

                val groupedArtifacts = allArtifacts
                  .groupBy(_.name)
                  .map {
                    case (name, artifacts) =>
                      val latestVersion = artifacts.maxBy(_.version).version
                      val filteredArtifacts = artifacts.filter(_.version == latestVersion)
                      (name, latestVersion, filteredArtifacts)
                  }
                  .filter {
                    case (_, _, artifacts) =>
                      params.binaryVersions
                        .forall(binaryVersion => artifacts.exists(_.binaryVersion == binaryVersion))
                  }
                  .toSeq
                  .sortBy { case (name, version, _) => (version, name) }(
                    Ordering.Tuple2(Version.ordering.reverse, Artifact.Name.ordering)
                  )
                val page = html.artifacts(env, user, project, header, groupedArtifacts, params, binaryVersions)
                complete(page)
            }
          }
        }
      },
      get {
        path(projectM / "artifacts" / artifactNameM) { (ref, artifactName) =>
          artifactsParams { params =>
            getProjectOrRedirect(ref, user) { project =>
              val artifactsF = database.getProjectArtifacts(ref, artifactName, params.stableOnly)
              val headerF = projectService.getHeader(project).map(_.get)
              for artifacts <- artifactsF; header <- headerF yield
                val binaryVersions = artifacts
                  .map(_.binaryVersion)
                  .distinct
                  .sorted(BinaryVersion.ordering.reverse)

                val artifactsByVersion = artifacts
                  .groupBy(_.version)
                  .filter {
                    case (_, artifacts) =>
                      params.binaryVersions.forall(binaryVersion => artifacts.exists(_.binaryVersion == binaryVersion))
                  }
                  .map { case (version, artifacts) => (artifacts.map(_.releaseDate).min, version) -> artifacts }
                val sortedArtifactsByVersion = SortedMap.from(artifactsByVersion)(
                  Ordering.Tuple2(Ordering[Instant].reverse, Ordering[Version].reverse)
                )
                val page = html.versions(
                  env,
                  user,
                  project,
                  header,
                  artifactName,
                  binaryVersions,
                  sortedArtifactsByVersion,
                  params
                )
                complete(page)
              end for
            }
          }
        }
      },
      get {
        path(projectM / "artifacts" / artifactNameM / versionM) { (ref, artifactName, artifactVersion) =>
          artifactParams { params =>
            getProjectOrRedirect(ref, user) { project =>
              val headerF = projectService.getHeader(project)
              val artifactsF = database.getProjectArtifacts(ref, artifactName, artifactVersion)
              for
                artifacts <- artifactsF
                header <- headerF
                binaryVersions = artifacts.map(_.binaryVersion).distinct.sorted(BinaryVersion.ordering.reverse)
                binaryVersion = params.binaryVersion.getOrElse(binaryVersions.head)
                artifact = artifacts.find(_.binaryVersion == binaryVersion).get
                directDepsF = database.getDirectDependencies(artifact)
                reverseDepsF = database.getReverseDependencies(artifact)
                directDeps <- directDepsF
                reverseDeps <- reverseDepsF
              yield
                val page = html.artifact(
                  env,
                  user,
                  project,
                  header,
                  artifact,
                  binaryVersions,
                  params,
                  directDeps,
                  reverseDeps
                )
                complete(page)
              end for
            }
          }
        }
      },
      get {
        path(projectM / "version-matrix") { ref =>
          getProjectOrRedirect(ref, user) { project =>
            for
              artifacts <- projectService.getArtifactRefs(project.reference, None, None, false)
              header <- projectService.getHeader(project)
            yield
              val binaryVersionByPlatforms = artifacts
                .map(_.binaryVersion)
                .distinct
                .groupBy(_.platform)
                .view
                .mapValues(_.sorted(BinaryVersion.ordering.reverse))
                .toSeq
                .sortBy(_._1)(Platform.ordering.reverse)

              val artifactsByVersions = artifacts
                .groupBy(_.version)
                .view
                .mapValues(artifacts => artifacts.groupMap(_.name)(_.binaryVersion).toSeq.sortBy(_._1))
                .toSeq
                .sortBy(_._1)(Version.ordering.reverse)
              val page = html.versionMatrix(env, user, project, header, binaryVersionByPlatforms, artifactsByVersions)
              complete(page)
          }
        }
      },
      get {
        path(projectM / "badges")(ref => getBadges(ref, user))
      },
      get {
        path(projectM / "settings") { projectRef =>
          user match
            case Some(userState) if userState.canEdit(projectRef, env) =>
              getEditPage(projectRef, userState)
            case _ =>
              complete((StatusCodes.Forbidden, forbidden(env, user)))
        }
      },
      post {
        path(projectM / "settings") { projectRef =>
          editForm { form =>
            val updateF = for
              _ <- database.updateProjectSettings(projectRef, form)
              _ <- artifactService.updateLatestVersions(projectRef, form.preferStableVersion)
              _ <- searchSynchronizer.syncProject(projectRef)
            yield ()
            onComplete(updateF) {
              case Success(()) => redirect(Uri(s"/$projectRef"), StatusCodes.SeeOther)
              case Failure(e) =>
                logger.error(s"Cannot save settings of project $projectRef", e)
                redirect(Uri(s"/$projectRef"), StatusCodes.SeeOther) // maybe we can print that it wasn't saved
            }
          }
        }
      },
      get {
        // redirect to new artifacts page
        path(projectM / artifactNameM)((projectRef, artifactName) =>
          parameter("binaryVersion".?) { binaryVersion =>
            val filter = binaryVersion.map(bv => s"?binary-version=$bv").getOrElse("")
            redirect(s"/$projectRef/artifacts/$artifactName$filter", StatusCodes.MovedPermanently)
          }
        )
      },
      get {
        // redirect to new artifact page
        path(projectM / artifactNameM / versionM)((projectRef, artifactName, version) =>
          parameter("binaryVersion".?) { binaryVersion =>
            val filter = binaryVersion.map(bv => s"binary-version=$bv").getOrElse("")
            redirect(s"/$projectRef/artifacts/$artifactName/$version?$filter", StatusCodes.MovedPermanently)
          }
        )
      }
    )

  private def getProjectOrRedirect(ref: Project.Reference, user: Option[UserState])(
      f: Project => Future[Route]
  ): Route =
    extractUri { uri =>
      val future = database.getProject(ref).flatMap {
        case Some(project) if project.githubStatus.isMoved =>
          val destination = project.githubStatus.asInstanceOf[GithubStatus.Moved].destination
          val redirectUri = uri.toString.replaceFirst(
            Pattern.quote(ref.toString),
            Matcher.quoteReplacement(destination.toString)
          )
          Future.successful(redirect(redirectUri, StatusCodes.MovedPermanently))
        case Some(project) => f(project)
        case None =>
          logger.warn(s"Project $ref not found")
          Future.successful(complete(StatusCodes.NotFound, notfound(env, user)))
      }
      onSuccess(future)(identity)
    }

  private val artifactsParams: Directive1[ArtifactsPageParams] =
    parameters(
      "binary-version".repeated,
      "stable-only".as[Boolean].withDefault(false)
    ).tmap {
      case (rawbinaryVersions, preReleases) =>
        val binaryVersions = rawbinaryVersions
          .flatMap(BinaryVersion.parse)
          .toSeq
          .sorted(Ordering[BinaryVersion].reverse)
        Tuple1(ArtifactsPageParams(binaryVersions, preReleases))
    }

  private val artifactParams: Directive1[ArtifactPageParams] =
    parameter("binary-version".?).map {
      case binaryVersion =>
        val binaryVersionsParsed = binaryVersion.flatMap(BinaryVersion.parse)
        ArtifactPageParams(binaryVersionsParsed)
    }

  private def getEditPage(
      ref: Project.Reference,
      user: UserState
  ): Route =
    getProjectOrRedirect(ref, Some(user)) { project =>
      for
        artifacts <- projectService.getArtifactRefs(ref, None, None, false)
        header <- projectService.getHeader(project)
      yield
        val page = html.editproject(env, user, project, header, artifacts.map(_.name).distinct)
        complete(page)
    }

  private def getProjectPage(ref: Project.Reference, user: Option[UserState]): Route =
    getProjectOrRedirect(ref, user) { project =>
      for
        header <- projectService.getHeader(project)
        directDependencies <-
          header
            .map(h => database.getProjectDependencies(ref, h.latestVersion))
            .getOrElse(Future.successful(Seq.empty))
        reverseDependencies <- database.getProjectDependents(ref)
      yield
        val groupedDirectDependencies = directDependencies
          .groupBy(_.target)
          .view
          .mapValues { deps =>
            val scope = deps.map(_.scope).min
            val versions = deps.map(_.targetVersion).distinct
            (scope, versions)
          }
        val groupedReverseDependencies = reverseDependencies
          .groupBy(_.source)
          .view
          .mapValues { deps =>
            val scope = deps.map(_.scope).min
            val version = deps.map(_.targetVersion).max
            (scope, version)
          }
        val page =
          html.project(env, user, project, header, groupedDirectDependencies.toMap, groupedReverseDependencies.toMap)
        complete(page)
    }

  private def getBadges(ref: Project.Reference, user: Option[UserState]): Route =
    getProjectOrRedirect(ref, user) { project =>
      for header <- projectService.getHeader(project).map(_.get) yield
        val artifact = header.getDefaultArtifact(None, None)
        val page = html.badges(env, user, project, header, artifact)
        complete(StatusCodes.OK, page)
    }

  private val editForm: Directive1[Project.Settings] =
    formFieldSeq.tflatMap(fields =>
      formFields(
        "category".?,
        "chatroom".?,
        "contributorsWanted".as[Boolean].?(false),
        "defaultArtifact".?,
        "preferStableVersion".as[Boolean].?(false),
        "deprecatedArtifacts".as[String].*,
        "cliArtifacts".as[String].*,
        "customScalaDoc".?
      ).tmap {
        case (
              rawCategory,
              rawChatroom,
              contributorsWanted,
              rawDefaultArtifact,
              preferStableVersion,
              rawDeprecatedArtifacts,
              rawCliArtifacts,
              rawCustomScalaDoc
            ) =>
          val documentationLinks =
            fields._1
              .filter { case (key, _) => key.startsWith("documentationLinks") }
              .groupBy {
                case (key, _) =>
                  key.drop("documentationLinks[".length).takeWhile(_ != ']')
              }
              .values
              .map { case Seq((a, b), (_, d)) => if a.contains("label") then (b, d) else (d, b) }
              .flatMap { case (label, link) => DocumentationPattern.validated(label, link) }
              .toSeq

          def noneIfEmpty(value: String): Option[String] =
            if value.isEmpty then None else Some(value)

          val settings: Project.Settings = Project.Settings(
            preferStableVersion,
            rawDefaultArtifact.flatMap(noneIfEmpty).map(Artifact.Name.apply),
            rawCustomScalaDoc.flatMap(noneIfEmpty),
            documentationLinks,
            contributorsWanted,
            rawDeprecatedArtifacts.map(Artifact.Name.apply).toSet,
            rawCliArtifacts.map(Artifact.Name.apply).toSet,
            rawCategory.flatMap(Category.byLabel.get),
            rawChatroom.flatMap(noneIfEmpty)
          )
          Tuple1(settings)
      }
    )
end ProjectPages
