package scaladex.server.route

import java.time.Instant

import scala.collection.MapView
import scala.collection.immutable.SortedMap
import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.concat
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Directives.parameters
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Artifact
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Env
import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.Project
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
          } yield artifact.scaladoc(project.flatMap(_.settings.customScalaDoc)).map(Uri.apply)

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
        path(organizationM / repositoryM / "artifacts") { (org, repo) =>
          artifactsParams { params =>
            val ref = Project.Reference(org, repo)
            val res =
              for {
                projectOpt <- database.getProject(ref)
                project = projectOpt.getOrElse(throw new Exception(s"project ${ref} not found"))
                uniqueArtifacts <- database.getUniqueArtifacts(ref)
                defaultArtifactName = params.artifactName.getOrElse(
                  project.settings.defaultArtifact.getOrElse(ArtifactPages.getDefaultArtifact(uniqueArtifacts))
                )
                artifacts <- database.getArtifacts(project.reference, defaultArtifactName, params)
                numberOfVersions <- database.countVersions(ref)
                lastVersion <- database.getLastVersion(ref)
              } yield (
                project,
                artifacts,
                defaultArtifactName,
                uniqueArtifacts.map(_._1),
                numberOfVersions,
                lastVersion
              )

            onComplete(res) {
              case Success((project, artifacts, defaultArtifactName, artifactNames, numberOfVersions, lastVersion)) =>
                val binaryVersions = artifacts
                  .map(_.binaryVersion)
                  .distinct
                  .sorted(BinaryVersion.ordering.reverse)

                val artifactPerVersion = artifacts.groupBy(_.version).map {
                  case (version, artifacts) => (artifacts.map(_.releaseDate).min, version) -> artifacts
                }
                val filteredArtifacts = filter(artifactPerVersion, params)
                val artifactsWithVersions =
                  SortedMap.from(
                    filteredArtifacts
                  )(Ordering.Tuple2(Ordering[Instant].reverse, Ordering[SemanticVersion].reverse))
                complete(
                  view.project.html
                    .artifacts(
                      env,
                      project,
                      user,
                      defaultArtifactName,
                      artifactNames.distinct,
                      binaryVersions,
                      artifactsWithVersions,
                      params,
                      lastVersion,
                      numberOfVersions
                    )
                )
              case Failure(e) =>
                logger.warn(s"failure when accessing ${ref}/artifacts", e)
                complete(StatusCodes.NotFound, view.html.notfound(env, user))
            }
          }
        }
      },
      get {
        path(projectM / "artifacts" / artifactM / versionM) { (ref, artifactName, artifactVersion) =>
          artifactParams { params =>
            val res =
              for {
                projectOpt <- database.getProject(ref)
                project = projectOpt.getOrElse(throw new Exception(s"project ${ref} not found"))
                artifacts <- database.getArtifacts(ref, artifactName, artifactVersion).map(_.groupBy(_.binaryVersion))
                currentVersion = params.binaryVersion.getOrElse(artifacts.keys.toSeq.sorted.head)
                numberOfVersions <- database.countVersions(ref)
                lastVersion <- database.getLastVersion(ref)
              } yield view.project.html
                .artifact(
                  env,
                  user,
                  project,
                  artifactName,
                  currentVersion,
                  artifacts,
                  artifactVersion,
                  params,
                  numberOfVersions,
                  lastVersion
                )

            onComplete(res) {
              case Success(html) =>
                complete(html)
              case Failure(_) =>
                complete(StatusCodes.NotFound, view.html.notfound(env, user))
            }
          }
        }
      },
      get {
        path(projectM / "version-matrix") { ref =>
          val res =
            for {
              projectOpt <- database.getProject(ref)
              project = projectOpt.getOrElse(throw new Exception(s"project ${ref} not found"))
              artifacts <- database.getArtifacts(project.reference)
              numberOfVersions <- database.countVersions(ref)
              lastProjectVersion <- database.getLastVersion(ref)
            } yield (project, artifacts, numberOfVersions, lastProjectVersion)

          onComplete(res) {
            case Success((project, artifacts, numberOfVersions, lastProjectVersion)) =>
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
            case Failure(_) =>
              complete(StatusCodes.NotFound, view.html.notfound(env, user))
          }
        }
      }
    )

  private val artifactsParams: Directive1[ArtifactsPageParams] =
    parameters(
      "binary-versions".repeated,
      "artifact-name".?,
      "show-non-semantic-versions".as[Boolean].withDefault(false)
    ).tmap {
      case (binaryVersions, artifactNameOpt, isSemantic) =>
        val binaryVersionsParsed = binaryVersions.flatMap(BinaryVersion.parse)
        val artifactName = artifactNameOpt.map(Artifact.Name(_))
        Tuple1(ArtifactsPageParams(binaryVersionsParsed.toSeq, artifactName, isSemantic))
    }

  private val artifactParams: Directive1[ArtifactPageParams] =
    parameter("binary-version".?).map {
      case binaryVersion =>
        val binaryVersionsParsed = binaryVersion.flatMap(BinaryVersion.parse)
        ArtifactPageParams(binaryVersionsParsed)
    }

  private def filter(
      artifacts: Map[(Instant, SemanticVersion), Seq[Artifact]],
      params: ArtifactsPageParams
  ): MapView[(Instant, SemanticVersion), Seq[Artifact]] =
    params.binaryVersions match {
      case Nil => artifacts.view
      case binaryVersions =>
        val filters = binaryVersions.map(b => (artifact: Artifact) => artifact.binaryVersion == b)
        val toKeep = artifacts
          .map {
            case ((version, releaseDate), artifacts) => (version, releaseDate) -> filters.forall(artifacts.exists)
          }
          .collect { case (toKeep, true) => toKeep }
          .toSet
        artifacts.view.filterKeys(toKeep)
    }

}
object ArtifactPages {
  def getDefaultArtifact(artifacts: Seq[(Artifact.Name, Platform, Language)]): Artifact.Name = {
    val res = artifacts.maxBy {
      case (name, platform, language) =>
        (
          platform,
          language,
          name
        )
    }(
      Ordering.Tuple3(
        Ordering[Platform],
        Ordering[Language],
        Ordering[Artifact.Name].reverse
      )
    )
    res._1
  }

}
