package ch.epfl.scala.index
package server
package routes

import model._
import data.project.ProjectForm
import release._
import model.misc._
import TwirlSupport._

import akka.http.scaladsl._
import model._
import server.Directives._
import Uri._
import StatusCodes._

import scala.collection.immutable.Seq
import scala.concurrent.Future

class ProjectPages(dataRepository: DataRepository, session: GithubUserSession) {

  import session._

  private def canEdit(owner: String, repo: String, userState: Option[UserState]) =
    userState.map(s => s.isAdmin || s.repos.contains(GithubRepo(owner, repo))).getOrElse(false)

  private def editPage(owner: String, repo: String, userState: Option[UserState]) = {
    val user = userState.map(_.user)
    if (canEdit(owner, repo, userState)) {
      for {
        keywords <- dataRepository.keywords()
        project <- dataRepository.project(Project.Reference(owner, repo))
      } yield {
        project.map { p =>
          val allKeywords = (p.keywords ++ keywords.keys.toSet).toList.sorted
          (OK, views.project.html.editproject(p, allKeywords, user))
        }.getOrElse((NotFound, views.html.notfound(user)))
      }
    } else Future.successful((Forbidden, views.html.forbidden(user)))
  }

  private def projectPage(owner: String,
                          repo: String,
                          artifact: Option[String],
                          version: Option[SemanticVersion],
                          userState: Option[UserState],
                          statusCode: StatusCode = OK) = {

    val user = userState.map(_.user)

    dataRepository
      .project(Project.Reference(owner, repo))
      .map(_.map(project =>
        (statusCode,
          views.project.html.project(
            project,
            user,
            canEdit(owner, repo, userState)
          ))
      ).getOrElse((NotFound, views.html.notfound(user))))
  }

  private def artifactPage(owner: String,
                           repo: String,
                           artifact: String,
                           version: Option[SemanticVersion],
                           userState: Option[UserState],
                           statusCode: StatusCode = OK) = {

    val user = userState.map(_.user)

    dataRepository
      .artifactPage(Project.Reference(owner, repo), ReleaseSelection(Some(artifact), version))
      .map(_.map {
        case (project, releases, release) =>
          (statusCode,
            views.artifact.html.artifact(
              project,
              releases,
              release,
              user,
              canEdit(owner, repo, userState)
            ))
      }.getOrElse((NotFound, views.html.notfound(user))))
  }

  // TODO: The user argument not being used seems suspicious, suggests there may actually be no authentication on the update
  def updateProjectBehavior(organization: String, repository: String, user: Option[UserState], fields: Seq[(String, String)], contributorsWanted: Boolean, keywords: Iterable[String], defaultArtifact: Option[String], defaultStableVersion: Boolean, deprecated: Boolean, artifactDeprecations: Iterable[String], cliArtifacts: Iterable[String], customScalaDoc: Option[String]) = {
    val documentationLinks = getDocumentationLinks(fields)

    onSuccess(
      dataRepository.updateProject(
        Project.Reference(organization, repository),
        ProjectForm(
          contributorsWanted,
          keywords.toSet,
          defaultStableVersion,
          deprecated,
          artifactDeprecations.toSet,
          cliArtifacts.toSet,
          customScalaDoc,
          documentationLinks
        )
      )
    ) { ret =>
      Thread.sleep(1000) // oh yeah
      redirect(Uri(s"/$organization/$repository"), SeeOther)
    }
  }

  def getEditPageBehavior(organization: String, repository: String, user: Option[UserState]) = {
    complete(editPage(organization, repository, user))
  }

  private def getDocumentationLinks(fields: Seq[(String, String)]) = {
    val documentationLinks = {
      val name = "documentationLinks"
      val end = "]".head

      fields.filter { case (key, _) => key.startsWith(name) }.groupBy {
        case (key, _) => key.drop("documentationLinks[".length).takeWhile(_ != end)
      }.values.map {
        case Vector((a, b), (c, d)) =>
          if (a.contains("label")) (b, d)
          else (d, b)
      }.toList
    }
    documentationLinks
  }

  def legacyArtifactQueryBehavior(organization: String, repository: String, artifact: String, version: Option[String]) = {
    val rest = version match {
      case Some(v) if !v.isEmpty => "/" + v
      case _ => ""
    }
    redirect(s"/$organization/$repository/$artifact$rest",
      StatusCodes.PermanentRedirect)
  }

  def projectPageBehavior(organization: String, repository: String, user: Option[UserState]) = {
    complete(projectPage(organization, repository, None, None, user))
  }

  def artifactPageBehavior(organization: String, repository: String, artifact: String, user: Option[UserState]) = {
    complete(
      artifactPage(organization, repository, artifact, None, user))
  }

  def artifactWithVersionBehavior(organization: String, repository: String, artifact: String, version: String, user: Option[UserState]) = {
    complete(
      artifactPage(organization,
        repository,
        artifact,
        SemanticVersion(version),
        user))
  }
}
