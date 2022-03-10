package scaladex.server.route
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
import scaladex.core.model.ArtifactDependency.Scope
import scaladex.core.model._
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
      post {
        path("edit" / projectM) { projectRef =>
          editForm { form =>
            val updateF = for {
              _ <- database.updateProjectSettings(projectRef, form)
              _ <- searchSynchronizer.syncProject(projectRef)
            } yield ()
            onComplete(updateF) {
              case Success(()) =>
                redirect(
                  Uri(s"/$projectRef"),
                  StatusCodes.SeeOther
                )
              case Failure(e) =>
                logger.error(s"Cannot save settings of project $projectRef", e)
                redirect(
                  Uri(s"/$projectRef"),
                  StatusCodes.SeeOther
                ) // maybe we can print that it wasn't saved
            }
          }
        }
      },
      get {
        path("edit" / projectM) { projectRef =>
          user match {
            case Some(userState) if userState.canEdit(projectRef, env) =>
              complete(getEditPage(projectRef, userState))
            case maybeUser =>
              complete((StatusCodes.Forbidden, view.html.forbidden(env, maybeUser)))
          }
        }
      },
      get {
        path(projectM)(projectRef =>
          parameters("artifact".?, "version".?, "binaryVersion".?, "selected".?) {
            (artifact, version, binaryVersion, selected) =>
              val fut: Future[StandardRoute] = database.getProject(projectRef).flatMap {
                case Some(Project(_, _, _, GithubStatus.Moved(_, newProjectRef), _, _)) =>
                  Future.successful(redirect(Uri(s"/$newProjectRef"), StatusCodes.PermanentRedirect))
                case Some(project) =>
                  val artifactRouteF: Future[StandardRoute] =
                    getSelectedArtifact(
                      database,
                      project,
                      binaryVersion = binaryVersion,
                      artifact = artifact.map(Artifact.Name.apply),
                      version = version,
                      selected = selected
                    ).map(_.map { artifact =>
                      val binaryVersionParam = s"?binaryVersion=${artifact.binaryVersion.label}"
                      redirect(
                        s"/$projectRef/${artifact.artifactName}/${artifact.version.encode}/$binaryVersionParam",
                        StatusCodes.TemporaryRedirect
                      )
                    }.getOrElse(complete(StatusCodes.NotFound, view.html.notfound(env, user))))
                  artifactRouteF
                case None =>
                  Future.successful(
                    complete(StatusCodes.NotFound, view.html.notfound(env, user))
                  )
              }
              onSuccess(fut)(identity)
          }
        )
      },
      get {
        path(projectM / artifactM)((projectRef, artifact) =>
          parameter("binaryVersion".?) { binaryVersion =>
            val res = getProjectPage(
              projectRef,
              binaryVersion,
              artifact,
              None,
              user
            )
            onComplete(res) {
              case Success((code, some)) => complete(code, some)
              case Failure(_) =>
                complete(StatusCodes.NotFound, view.html.notfound(env, user))
            }
          }
        )
      },
      get {
        path(projectM / artifactM / versionM)((projectRef, artifact, version) =>
          parameter("binaryVersion".?) { binaryVersion =>
            val res = getProjectPage(projectRef, binaryVersion, artifact, Some(version), user)
            onComplete(res) {
              case Success((code, some)) => complete(code, some)
              case Failure(_) =>
                complete(StatusCodes.NotFound, view.html.notfound(env, user))
            }
          }
        )
      }
    )

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
      projectRef: Project.Reference,
      binaryVersion: Option[String],
      artifact: Artifact.Name,
      version: Option[SemanticVersion],
      user: Option[UserState]
  ): Future[(StatusCode, HtmlFormat.Appendable)] = {
    val selection = ArtifactSelection.parse(
      binaryVersion = binaryVersion,
      artifactName = Some(artifact),
      version = version.map(_.toString),
      selected = None
    )
    database.getProject(projectRef).flatMap {
      case Some(project) =>
        for {
          artifacts <- database.getArtifacts(projectRef)
          selectedArtifact = selection
            .defaultArtifact(artifacts, project)
            .getOrElse(throw new Exception(s"no artifact found for $projectRef"))
          lastVersion <- database.getLastVersion(projectRef)
          directReleaseDependencies <- database.getDirectReleaseDependencies(projectRef, lastVersion)
          reverseReleaseDependencies <- database.getReverseReleaseDependencies(projectRef, lastVersion)
        } yield {
          val allVersions = artifacts.view.map(_.version)
          val binaryVersions = artifacts.view.map(_.binaryVersion)
          val platformsForBadges = artifacts.view
            .filter(_.artifactName == selectedArtifact.artifactName)
            .map(_.binaryVersion.platform)
          val artifactNames = artifacts.view.map(_.artifactName)
          val twitterCard = project.twitterSummaryCard
          val html = view.project.html.project(
            env,
            project,
            SortedSet.from(artifactNames)(Artifact.Name.ordering),
            SortedSet.from(allVersions)(SemanticVersion.ordering.reverse),
            SortedSet.from(binaryVersions)(BinaryVersion.ordering.reverse),
            SortedSet.from(platformsForBadges)(Platform.ordering.reverse),
            selectedArtifact,
            user,
            Some(twitterCard),
            artifacts.size,
            directReleaseDependencies.groupBy(d => (d.ref, d.scope)),
            keepLastVersion(reverseReleaseDependencies),
            lastVersion
          )
          (StatusCodes.OK, html)
        }
      case None =>
        Future.successful((StatusCodes.NotFound, view.html.notfound(env, user)))
    }
  }

  def keepLastVersion(
      reverse: Seq[ReleaseDependency.Result]
  ): Map[(Project.Reference, Scope), Seq[ReleaseDependency.Result]] =
    reverse.groupBy(d => (d.ref, d.scope)).map {
      case (key, releaseDependencies) => key -> releaseDependencies.maxByOption(_.releaseDate).toSeq
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
                  key
                    .drop("documentationLinks[".length)
                    .takeWhile(_ != ']')
              }
              .values
              .map {
                case Vector((a, b), (_, d)) =>
                  if (a.contains("label")) (b, d)
                  else (d, b)
              }
              .flatMap {
                case (label, link) =>
                  Project.DocumentationLink.from(label, link)
              }
              .toList

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

  private def getSelectedArtifact(
      database: WebDatabase,
      project: Project,
      binaryVersion: Option[String],
      artifact: Option[Artifact.Name],
      version: Option[String],
      selected: Option[String]
  ): Future[Option[Artifact]] = {
    val artifactSelection = ArtifactSelection.parse(
      binaryVersion = binaryVersion,
      artifactName = artifact,
      version = version,
      selected = selected
    )
    for {
      artifacts <- database.getArtifacts(project.reference)
      defaultArtifact = artifactSelection.defaultArtifact(artifacts, project)
    } yield defaultArtifact
  }
}
