package scaladex.server.route
import java.time.Instant

import scala.collection.SortedMap
import scala.collection.SortedSet
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import akka.http.scaladsl.model.Uri._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.LazyLogging
import play.twirl.api.HtmlFormat
import scaladex.core.model._
import scaladex.core.model.web.ArtifactPageParams
import scaladex.core.model.web.ArtifactsPageParams
import scaladex.core.service.SearchEngine
import scaladex.core.service.WebDatabase
import scaladex.server.TwirlSupport._
import scaladex.server.service.SearchSynchronizer
import scaladex.view

class ProjectPages(env: Env, database: WebDatabase, searchEngine: SearchEngine)(
    implicit executionContext: ExecutionContext
) extends LazyLogging {
  private val searchSynchronizer = new SearchSynchronizer(database, searchEngine)

  def route(user: Option[UserState]): Route =
    concat(
      get {
        path(projectM)(ref => onSuccess(getProjectPage(ref, user))(identity))
      },
      get {
        path(projectM / "artifacts") { ref =>
          val future =
            database.getProject(ref).flatMap {
              case Some(project) =>
                for {
                  lastVersion <- database.getLastVersion(ref)
                  artifacts <- database.getArtifactsByVersion(ref, lastVersion)
                } yield {
                  val defaultArtifactName = project.settings.defaultArtifact
                    .flatMap(name => artifacts.find(_.artifactName == name))
                    .getOrElse(ProjectPages.getDefault(artifacts))
                    .artifactName
                  redirect(s"/$ref/artifacts/$defaultArtifactName", StatusCodes.TemporaryRedirect)
                }
              case None =>
                Future.successful(complete(StatusCodes.NotFound, view.html.notfound(env, user)))
            }
          onSuccess(future)(identity)
        }
      },
      get {
        path(projectM / "artifacts" / artifactNameM) { (ref, artifactName) =>
          artifactsParams { params =>
            val future = database.getProject(ref).flatMap {
              case Some(project) =>
                for {
                  artifactNames <- database.getArtifactNames(ref)
                  artifacts <- database.getArtifacts(ref, artifactName, params)
                  numberOfVersions <- database.countVersions(ref)
                  lastVersion <- database.getLastVersion(ref)
                } yield {
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
                  val html = view.project.html.artifacts(
                    env,
                    project,
                    user,
                    artifactName,
                    artifactNames,
                    binaryVersions,
                    SortedMap.from(artifactsByVersion)(
                      Ordering.Tuple2(Ordering[Instant].reverse, Ordering[SemanticVersion].reverse)
                    ),
                    params,
                    lastVersion,
                    numberOfVersions
                  )
                  complete(html)
                }
              case None =>
                Future.successful(complete(StatusCodes.NotFound, view.html.notfound(env, user)))
            }
            onSuccess(future)(identity)
          }
        }
      },
      get {
        path(projectM / "artifacts" / artifactNameM / versionM) { (ref, artifactName, artifactVersion) =>
          artifactParams { params =>
            val future = database.getProject(ref).flatMap {
              case Some(project) =>
                for {
                  artifacts <- database.getArtifacts(ref, artifactName, artifactVersion)
                  binaryVersions = artifacts.map(_.binaryVersion).distinct.sortBy(_.platform)(Platform.ordering.reverse)
                  binaryVersion = params.binaryVersion.getOrElse(binaryVersions.head)
                  artifact = artifacts.find(_.binaryVersion == binaryVersion).get
                  directDeps <- database.getDirectDependencies(artifact)
                  reverseDeps <- database.getReverseDependencies(artifact)
                  numberOfVersions <- database.countVersions(ref)
                  lastVersion <- database.getLastVersion(ref)
                } yield {
                  val html = view.project.html
                    .artifact(
                      env,
                      user,
                      project,
                      artifact,
                      binaryVersions,
                      params,
                      directDeps,
                      reverseDeps,
                      numberOfVersions,
                      lastVersion
                    )
                  complete(html)
                }
              case None =>
                Future.successful(complete(StatusCodes.NotFound, view.html.notfound(env, user)))
            }
            onSuccess(future)(identity)
          }
        }
      },
      get {
        path(projectM / "version-matrix") { ref =>
          val future = database.getProject(ref).flatMap {
            case Some(project) =>
              for {
                artifacts <- database.getArtifacts(project.reference)
                numberOfVersions <- database.countVersions(ref)
                lastProjectVersion <- database.getLastVersion(ref)
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

                complete(
                  view.project.html
                    .versionMatrix(
                      env,
                      project,
                      user,
                      binaryVersionByPlatforms,
                      artifactsByVersions,
                      numberOfVersions,
                      lastProjectVersion
                    )
                )
              }
            case None =>
              Future.successful(complete(StatusCodes.NotFound, view.html.notfound(env, user)))
          }
          onSuccess(future)(identity)
        }
      },
      get {
        path(projectM / "badges")(ref => onSuccess(getBadges(ref, user))(identity))
      },
      get {
        path(projectM / "settings") { projectRef =>
          user match {
            case Some(userState) if userState.canEdit(projectRef, env) =>
              complete(getEditPage(projectRef, userState))
            case _ =>
              complete((StatusCodes.Forbidden, view.html.forbidden(env, user)))
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
            redirect(s"/$projectRef/artifacts/$artifactName$filter", StatusCodes.PermanentRedirect)
          }
        )
      },
      get {
        // redirect to new artifact page
        path(projectM / artifactNameM / versionM)((projectRef, artifactName, version) =>
          parameter("binaryVersion".?) { binaryVersion =>
            val filter = binaryVersion.map(bv => s"binary-version=$bv").getOrElse("")
            redirect(s"/$projectRef/artifacts/$artifactName/$version?$filter", StatusCodes.PermanentRedirect)
          }
        )
      }
    )

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
  ): Future[(StatusCode, HtmlFormat.Appendable)] =
    for {
      projectOpt <- database.getProject(ref)
      artifacts <- database.getArtifacts(ref)
      numberOfVersions <- database.countVersions(ref)
      lastVersion <- database.getLastVersion(ref)
    } yield projectOpt
      .map { p =>
        val page = view.project.html.editproject(env, p, artifacts, user, numberOfVersions, lastVersion)
        (StatusCodes.OK, page)
      }
      .getOrElse((StatusCodes.NotFound, view.html.notfound(env, Some(user))))

  private def getProjectPage(
      ref: Project.Reference,
      user: Option[UserState]
  ): Future[StandardRoute] = {
    val reverseDependenciesF = database.getReverseReleaseDependencies(ref)
    database.getProject(ref).flatMap {
      case Some(project) =>
        for {
          lastVersion <- database.getLastVersion(ref)
          versionCount <- database.countVersions(ref)
          directDependencies <- database.getDirectReleaseDependencies(ref, lastVersion)
          reverseDependencies <- reverseDependenciesF
        } yield {
          val groupedDirectDependencies = directDependencies
            .groupBy(_.targetRef)
            .view
            .mapValues { deps =>
              val scope = deps.map(_.scope).min
              val versions = deps.map(_.targetVersion).distinct
              (scope, versions)
            }
          val groupedReverseDependencies = reverseDependencies
            .groupBy(_.sourceRef)
            .view
            .mapValues { deps =>
              val scope = deps.map(_.scope).min
              val version = deps.map(_.targetVersion).max
              (scope, version)
            }
          val html = view.project.html.project(
            env,
            user,
            project,
            versionCount,
            lastVersion,
            groupedDirectDependencies.toMap,
            groupedReverseDependencies.toMap
          )
          complete(StatusCodes.OK, html)
        }

      case None =>
        Future.successful(complete(StatusCodes.NotFound, view.html.notfound(env, user)))
    }
  }

  private def getBadges(ref: Project.Reference, user: Option[UserState]): Future[StandardRoute] =
    database.getProject(ref).flatMap {
      case Some(project) =>
        for {
          lastVersion <- database.getLastVersion(ref)
          artifacts <- database.getArtifactsByVersion(ref, lastVersion)
          defaultArtifact =
            project.settings.defaultArtifact
              .flatMap(name => artifacts.find(_.artifactName == name))
              .getOrElse(ProjectPages.getDefault(artifacts))
          versionCount <- database.countVersions(ref)
          platforms <- database.getArtifactPlatforms(ref, defaultArtifact.artifactName)
        } yield {
          val html = view.project.html.badges(
            env,
            user,
            project,
            versionCount,
            lastVersion,
            defaultArtifact,
            SortedSet.from(platforms)(Platform.ordering.reverse)
          )
          complete(StatusCodes.OK, html)
        }

      case None =>
        Future.successful(complete(StatusCodes.NotFound, view.html.notfound(env, user)))
    }

  // TODO remove all unused parameters
  private val editForm: Directive1[Project.Settings] =
    formFieldSeq.tflatMap(fields =>
      formFields(
        "contributorsWanted".as[Boolean] ? false,
        "defaultArtifact".?,
        "defaultStableVersion".as[Boolean] ? false,
        "strictVersions".as[Boolean] ? false,
        "deprecated".as[Boolean] ? false,
        "artifactDeprecations".as[String].*,
        "cliArtifacts".as[String].*,
        "customScalaDoc".?,
        "category".?,
        "beginnerIssuesLabel".?,
        "selectedBeginnerIssues".as[String].*,
        "chatroom".?,
        "contributingGuide".?,
        "codeOfConduct".?
      ).tmap {
        case (
              contributorsWanted,
              rawDefaultArtifact,
              defaultStableVersion,
              strictVersions,
              deprecated,
              rawArtifactDeprecations,
              rawCliArtifacts,
              rawCustomScalaDoc,
              rawCategory,
              rawBeginnerIssuesLabel,
              _,
              _,
              _,
              _
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
            defaultStableVersion,
            rawDefaultArtifact.flatMap(noneIfEmpty).map(Artifact.Name.apply),
            strictVersions,
            rawCustomScalaDoc.flatMap(noneIfEmpty),
            documentationLinks,
            deprecated,
            contributorsWanted,
            rawArtifactDeprecations.map(Artifact.Name.apply).toSet,
            rawCliArtifacts.map(Artifact.Name.apply).toSet,
            rawCategory.flatMap(Category.byLabel.get),
            rawBeginnerIssuesLabel.flatMap(noneIfEmpty)
          )
          Tuple1(settings)
      }
    )
}

object ProjectPages {
  def getDefault(artifacts: Seq[Artifact]): Artifact =
    artifacts
      .maxBy(a => (a.platform, a.language, a.artifactName))(
        Ordering.Tuple3(Ordering[Platform], Ordering[Language], Ordering[Artifact.Name].reverse)
      )
}
