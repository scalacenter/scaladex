package ch.epfl.scala.index
package data
package project

import data.github.{GithubDownload, GithubReader}
import model.Project
import model.misc.{GithubInfo, Url}

case class ProjectForm(
    // project
    contributorsWanted: Boolean,
    keywords: Set[String],
    defaultArtifact: Option[String],
    defaultStableVersion: Boolean,
    deprecated: Boolean,
    artifactDeprecations: Set[String],
    cliArtifacts: Set[String],
    // documentation
    customScalaDoc: Option[String],
    documentationLinks: List[(String, String)],
    primaryTopic: Option[String] = None,
    beginnerIssuesLabel: Option[String],
    chatroom: Option[Url],
    contributingGuide: Option[Url],
    codeOfConduct: Option[Url]
) {
  def update(project: Project,
             paths: DataPaths,
             githubDownload: GithubDownload,
             defaultToProject: Boolean = false): Project = {

    val githubWithKeywords =
      if (project.github.isEmpty) {
        Some(GithubInfo(topics = keywords))
      } else {
        project.github.map(
          github => github.copy(topics = github.topics ++ keywords)
        )
      }

    val oldBeginnerIssueLabel = project.github.flatMap(_.beginnerIssuesLabel)
    val getBeginnerIssues = beginnerIssuesLabel != oldBeginnerIssueLabel
    val newBeginnerIssues =
      if (getBeginnerIssues) {
        githubDownload.runBeginnerIssues(project.githubRepo,
                                         beginnerIssuesLabel.getOrElse(""))
        GithubReader.beginnerIssues(paths, project.githubRepo).toOption
      } else None

    val newChatroom = chatroom.filterNot(_.target == "")
    val newContributingGuide = contributingGuide.filterNot(_.target == "")
    val newCodeOfConduct = codeOfConduct.filterNot(_.target == "")

    project.copy(
      contributorsWanted = contributorsWanted,
      defaultArtifact =
        if (defaultArtifact.isDefined) defaultArtifact
        else project.defaultArtifact,
      defaultStableVersion = defaultStableVersion,
      deprecated = deprecated,
      artifactDeprecations = artifactDeprecations,
      cliArtifacts = cliArtifacts,
      hasCli = cliArtifacts.nonEmpty,
      github = githubWithKeywords.map(
        github =>
          github.copy(
            beginnerIssuesLabel = beginnerIssuesLabel.filterNot(_ == ""),
            beginnerIssues = newBeginnerIssues.getOrElse(
              project.github
                .map(
                  _.beginnerIssues
                )
                .getOrElse(List())
            ),
            chatroom =
              if (defaultToProject && !newChatroom.isDefined)
                project.github.flatMap(_.chatroom)
              else newChatroom,
            contributingGuide =
              if (defaultToProject && !newContributingGuide.isDefined)
                project.github.flatMap(_.contributingGuide)
              else newContributingGuide,
            codeOfConduct =
              if (defaultToProject && !newCodeOfConduct.isDefined)
                project.github.flatMap(_.codeOfConduct)
              else newCodeOfConduct
        )
      ),
      customScalaDoc = customScalaDoc.filterNot(_ == ""),
      documentationLinks = documentationLinks.filterNot {
        case (label, link) => label == "" || link == ""
      },
      primaryTopic = primaryTopic
    )
  }
}

object ProjectForm {
  def apply(project: Project): ProjectForm = {
    import project._

    new ProjectForm(
      contributorsWanted,
      github.map(_.topics).getOrElse(Set()),
      defaultArtifact,
      defaultStableVersion,
      deprecated,
      artifactDeprecations,
      cliArtifacts,
      customScalaDoc,
      documentationLinks,
      primaryTopic,
      github.flatMap(_.beginnerIssuesLabel),
      github.flatMap(_.chatroom),
      github.flatMap(_.contributingGuide),
      github.flatMap(_.codeOfConduct)
    )
  }
}
