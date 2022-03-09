package scaladex.server.route

import akka.http.scaladsl.model.Uri._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.LazyLogging
import play.twirl.api.HtmlFormat
import scaladex.core.model._
import scaladex.core.service.{SearchEngine, WebDatabase}
import scaladex.server.TwirlSupport._
import scaladex.server.service.SearchSynchronizer
import scaladex.view

import scala.collection.{SeqView, SortedSet}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class ProjectPages2(env: Env, database: WebDatabase, searchEngine: SearchEngine)(
    implicit executionContext: ExecutionContext
) extends LazyLogging {
  private val searchSynchronizer = new SearchSynchronizer(database, searchEngine)

  def route(user: Option[UserState]): Route =
    pathPrefix("v2") {
      concat(
        post {
          path("edit" / organizationM / repositoryM) { (organization, repository) =>
            editForm { form =>
              val ref = Project.Reference(organization, repository)
              val updateF = for {
                _ <- database.updateProjectSettings(ref, form)
                _ <- searchSynchronizer.syncProject(ref)
              } yield ()
              onComplete(updateF) {
                case Success(()) =>
                  redirect(
                    Uri(s"/$organization/$repository"),
                    StatusCodes.SeeOther
                  )
                case Failure(e) =>
                  logger.error(s"Cannot save settings of project $ref", e)
                  redirect(
                    Uri(s"/$organization/$repository"),
                    StatusCodes.SeeOther
                  ) // maybe we can print that it wasn't saved
              }
            }
          }
        },
        get {
          path("artifacts" / organizationM / repositoryM) { (org, repo) =>
            val ref = Project.Reference(org, repo)
            val res =
              for {
                projectOpt <- database.getProject(ref)
                project = projectOpt.getOrElse(throw new Exception(s"project ${ref} not found"))
                artifacts <- database.getArtifacts(project.reference)
              } yield (project, artifacts)

            onComplete(res) {
              case Success((project, artifacts)) =>
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

                complete(view.html.artifacts(env, project, user, binaryVersionByPlatforms, artifactsByVersions))
              case Failure(e) =>
                complete(StatusCodes.NotFound, view.html.notfound(env, user))
            }
          }
        },
        get {
          path("edit" / organizationM / repositoryM) { (organization, repository) =>
            val projectRef = Project.Reference(organization, repository)
            user match {
              case maybeUser if canEdit(env, maybeUser, projectRef) =>
                complete(getEditPage(projectRef, maybeUser))
              case _ =>
                complete((StatusCodes.Forbidden, view.html.forbidden(env, user)))
            }
          }
        },
        get {
          path(organizationM / repositoryM)((organization, repository) =>
            parameters("artifact".?, "version".?, "binaryVersion".?, "selected".?) {
              (artifact, version, binaryVersion, selected) =>
                val projectRef = Project.Reference(organization, repository)
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
                          s"/$organization/$repository/${artifact.artifactName}/${artifact.version}/$binaryVersionParam",
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
          path(organizationM / repositoryM / artifactM)((organization, repository, artifact) =>
            parameter("binaryVersion".?) { binaryVersion =>
              val res = getProjectPage(
                organization,
                repository,
                binaryVersion,
                artifact,
                None,
                user
              )
              onComplete(res) {
                case Success((code, some)) => complete(code, some)
                case Failure(e) =>
                  complete(StatusCodes.NotFound, view.html.notfound(env, user))
              }
            }
          )
        },
        get {
          path(organizationM / repositoryM / artifactM / versionM)((organization, repository, artifact, version) =>
            parameter("binaryVersion".?) { binaryVersion =>
              val res = getProjectPage(organization, repository, binaryVersion, artifact, Some(version), user)
              onComplete(res) {
                case Success((code, some)) => complete(code, some)
                case Failure(e) =>
                  complete(StatusCodes.NotFound, view.html.notfound(env, user))
              }
            }
          )
        }
      )
    }

  private def getEditPage(ref: Project.Reference, maybeUser: Option[UserState]): Future[(StatusCode, HtmlFormat.Appendable)] =
    for {
      projectOpt <- database.getProject(ref)
      artifacts <- database.getArtifacts(ref)
    } yield projectOpt
      .map { p =>
        val page = view.project.html.editproject(env, p, artifacts, maybeUser)
        (StatusCodes.OK, page)
      }
      .getOrElse((StatusCodes.NotFound, view.html.notfound(env, maybeUser)))

  private def filterVersions(p: Project, allVersions: SeqView[SemanticVersion]): SortedSet[SemanticVersion] = {
    val filtered =
      if (p.settings.strictVersions) allVersions.view.filter(_.isSemantic)
      else allVersions.view
    SortedSet.from(filtered)(SemanticVersion.ordering.reverse)
  }

  private def getProjectPage(
      organization: Project.Organization,
      repository: Project.Repository,
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
    val projectRef =
      Project.Reference(organization, repository)

    database.getProject(projectRef).flatMap {
      case Some(project) =>
        for {
          artifacts <- database.getArtifacts(projectRef)
          selectedArtifact = selection
            .defaultArtifact(artifacts, project)
            .getOrElse(throw new Exception(s"no artifact found for $projectRef"))
          directDependencies <- database.getDirectDependencies(selectedArtifact)
          reverseDependency <- database.getReverseDependencies(selectedArtifact)
        } yield {
          val allVersions = artifacts.view.map(_.version)
          val filteredVersions = filterVersions(project, allVersions)
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
            filteredVersions,
            SortedSet.from(binaryVersions)(BinaryVersion.ordering.reverse),
            SortedSet.from(platformsForBadges)(Platform.ordering.reverse),
            selectedArtifact,
            user,
            showEditButton = env.isLocal || user.exists(_.canEdit(projectRef)), // show only when your are admin on the project
            Some(twitterCard),
            artifacts.size,
            directDependencies,
            reverseDependency
          )
          (StatusCodes.OK, html)
        }
      case None =>
        Future.successful((StatusCodes.NotFound, view.html.notfound(env, user)))
    }
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
              selectedBeginnerIssues,
              rawChatroom,
              rawContributingGuide,
              rawCodeOfConduct
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

  private def canEdit(env: Env, userStateOpt: Option[UserState], projectRef: Project.Reference): Boolean =
    env.isLocal || userStateOpt.exists(_.canEdit(projectRef))
}
