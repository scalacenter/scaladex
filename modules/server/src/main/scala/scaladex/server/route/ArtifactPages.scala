package scaladex.server.route

import java.time.Instant

import scala.collection.immutable.SortedMap
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Success

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Artifact
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Env
import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.SemanticVersion
import scaladex.core.model.UserState
import scaladex.core.model.web.ArtifactPageParams
import scaladex.core.model.web.ArtifactsPageParams
import scaladex.core.service.WebDatabase
import scaladex.server.TwirlSupport._
import scaladex.view
import scaladex.view.html

class ArtifactPages(env: Env, database: WebDatabase)(implicit executionContext: ExecutionContext) extends LazyLogging {
  def route(user: Option[UserState]): Route =
    concat(
      get {
        path("artifacts" / mavenReferenceM / "scaladoc" ~ RemainingPath) { (mavenRef, dri) =>
          val scaladocUriF = for {
            artifact <- database.getArtifactByMavenReference(mavenRef).map(_.get)
            project <- database.getProject(artifact.projectRef)
          } yield project.flatMap(_.scaladoc(artifact).map(doc => Uri(doc.link)))

          onComplete(scaladocUriF) {
            case Success(Some(scaladocUri)) =>
              val finalUri = scaladocUri.withPath(scaladocUri.path ++ dri)
              redirect(finalUri, StatusCodes.SeeOther)
            case _ =>
              complete(StatusCodes.NotFound, html.notfound(env, user))
          }
        }
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
                    .getOrElse(ArtifactPages.getDefault(artifacts))
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
                  artifacts <- database.getArtifacts(ref, artifactName, artifactVersion).map(_.groupBy(_.binaryVersion))
                  currentVersion = params.binaryVersion.getOrElse(artifacts.keys.toSeq.max(BinaryVersion.ordering))
                  currentArtifact = artifacts(currentVersion).head
                  directDeps <- database.getDirectDependencies(currentArtifact)
                  reverseDeps <- database.getReverseDependencies(currentArtifact)
                  numberOfVersions <- database.countVersions(ref)
                  lastVersion <- database.getLastVersion(ref)
                } yield {
                  val html = view.project.html
                    .artifact(
                      env,
                      user,
                      project,
                      artifactName,
                      currentArtifact,
                      artifacts,
                      artifactVersion,
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

}

object ArtifactPages {
  def getDefault(artifacts: Seq[Artifact]): Artifact =
    artifacts
      .maxBy(a => (a.platform, a.language, a.artifactName))(
        Ordering.Tuple3(Ordering[Platform], Ordering[Language], Ordering[Artifact.Name].reverse)
      )

}
