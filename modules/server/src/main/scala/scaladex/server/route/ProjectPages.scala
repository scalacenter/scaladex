package scaladex.server.route
import java.time.Instant
import java.util.regex.Matcher
import java.util.regex.Pattern

import scala.collection.SortedMap
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.http.scaladsl.model.Uri._
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server._
import scaladex.core.model._
import scaladex.core.service.SearchEngine
import scaladex.core.service.WebDatabase
import scaladex.core.web.ArtifactPageParams
import scaladex.core.web.ArtifactsPageParams
import scaladex.server.TwirlSupport._
import scaladex.server.service.SearchSynchronizer
import scaladex.view.html.forbidden
import scaladex.view.html.notfound
import scaladex.view.model.ProjectHeader
import scaladex.view.project.html

class ProjectPages(env: Env, database: WebDatabase, searchEngine: SearchEngine)(
    implicit executionContext: ExecutionContext
) extends LazyLogging {
  private val searchSynchronizer = new SearchSynchronizer(database, searchEngine)

  def route(user: Option[UserState]): Route =
    concat(
      routes = get {
        path(projectM)(getProjectPage(_, user))
      },
      get {
        path(projectM / "artifacts" / artifactNameM) { (ref, artifactName) =>
          artifactsParams { params =>
            getProjectOrRedirect(ref, user) { project =>
              val artifactsF = database.getArtifacts(ref, artifactName, params)
              val headerF = getProjectHeader(project).map(_.get)
              for (artifacts <- artifactsF; header <- headerF) yield {
                val binaryVersions = artifacts
                  .map(_.binaryVersion)
                  .distinct
                  .sorted(BinaryVersion.ordering.reverse)

                val artifactsByVersion =
                  artifacts
                    .groupBy(_.version)
                    .filter {
                      case (_, artifacts) =>
                        params.binaryVersions
                          .forall(binaryVersion => artifacts.exists(_.binaryVersion == binaryVersion))
                    }
                    .map { case (version, artifacts) => (artifacts.map(_.releaseDate).min, version) -> artifacts }
                val sortedArtifactsByVersion = SortedMap.from(artifactsByVersion)(
                  Ordering.Tuple2(Ordering[Instant].reverse, Ordering[SemanticVersion].reverse)
                )
                val page = html.artifacts(
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
              }
            }
          }
        }
      },
      get {
        path(projectM / "artifacts" / artifactNameM / versionM) { (ref, artifactName, artifactVersion) =>
          artifactParams { params =>
            getProjectOrRedirect(ref, user) { project =>
              val headerF = getProjectHeader(project)
              val artifactsF = database.getArtifacts(ref, artifactName, artifactVersion)
              for {
                artifacts <- artifactsF
                header <- headerF
                binaryVersions = artifacts.map(_.binaryVersion).distinct.sorted(BinaryVersion.ordering.reverse)
                binaryVersion = params.binaryVersion.getOrElse(binaryVersions.head)
                artifact = artifacts.find(_.binaryVersion == binaryVersion).get
                directDepsF = database.getDirectDependencies(artifact)
                reverseDepsF = database.getReverseDependencies(artifact)
                directDeps <- directDepsF
                reverseDeps <- reverseDepsF
              } yield {
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
              }
            }
          }
        }
      },
      get {
        path(projectM / "version-matrix") { ref =>
          getProjectOrRedirect(ref, user) { project =>
            for {
              artifacts <- database.getArtifacts(project.reference)
              header <- getProjectHeader(project)
            } yield {
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
                .map { case (version, artifacts) => (version, artifacts.groupBy(_.artifactName).toSeq.sortBy(_._1)) }
                .toSeq
                .sortBy(_._1)(SemanticVersion.ordering.reverse)
              val page = html.versionMatrix(env, user, project, header, binaryVersionByPlatforms, artifactsByVersions)
              complete(page)
            }
          }
        }
      },
      get {
        path(projectM / "badges")(ref => getBadges(ref, user))
      },
      get {
        path(projectM / "settings") { projectRef =>
          user match {
            case Some(userState) if userState.canEdit(projectRef, env) =>
              getEditPage(projectRef, userState)
            case _ =>
              complete((StatusCodes.Forbidden, forbidden(env, user)))
          }
        }
      },
      post {
        path(projectM / "settings") { projectRef =>
          editForm { form =>
            val updateF = for {
              _ <- database.updateProjectSettings(projectRef, form)
              _ <- searchSynchronizer.syncProject(projectRef)
            } yield ()
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
            val filter = binaryVersion.map(bv => s"?binary-versions=$bv").getOrElse("")
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
      "binary-versions".repeated,
      "pre-releases".as[Boolean].withDefault(false)
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
      for {
        artifacts <- database.getArtifacts(ref)
        header <- getProjectHeader(project)
      } yield {
        val page = html.editproject(env, user, project, header, artifacts)
        complete(page)
      }
    }

  private def getProjectHeader(project: Project): Future[Option[ProjectHeader]] = {
    val ref = project.reference
    for {
      latestArtifacts <- database.getLatestArtifacts(ref, project.settings.preferStableVersion)
      versionCount <- database.countVersions(ref)
    } yield ProjectHeader(
      project.reference,
      latestArtifacts,
      versionCount,
      project.settings.defaultArtifact,
      project.settings.preferStableVersion
    )
  }

  private def getProjectPage(ref: Project.Reference, user: Option[UserState]): Route =
    getProjectOrRedirect(ref, user) { project =>
      for {
        header <- getProjectHeader(project)
        directDependencies <-
          header
            .map(h => database.getProjectDependencies(ref, h.defaultVersion))
            .getOrElse(Future.successful(Seq.empty))
        reverseDependencies <- database.getProjectDependents(ref)
      } yield {
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
    }

  private def getBadges(ref: Project.Reference, user: Option[UserState]): Route =
    getProjectOrRedirect(ref, user) { project =>
      for (header <- getProjectHeader(project).map(_.get)) yield {
        val artifact = header.getDefaultArtifact(None, None)
        val page = html.badges(env, user, project, header, artifact)
        complete(StatusCodes.OK, page)
      }
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
              .map { case Seq((a, b), (_, d)) => if (a.contains("label")) (b, d) else (d, b) }
              .flatMap { case (label, link) => DocumentationPattern.validated(label, link) }
              .toSeq

          def noneIfEmpty(value: String): Option[String] =
            if (value.isEmpty) None else Some(value)

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
}
