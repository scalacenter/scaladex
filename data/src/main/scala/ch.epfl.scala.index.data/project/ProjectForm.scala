package ch.epfl.scala.index
package data
package project

import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.github.GithubReader
import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubIssue
import ch.epfl.scala.index.model.misc.Url

case class ProjectForm(
    // project
    contributorsWanted: Boolean,
    keywords: Set[String],
    defaultArtifact: Option[String],
    defaultStableVersion: Boolean,
    strictVersions: Boolean = false,
    deprecated: Boolean,
    artifactDeprecations: Set[String],
    cliArtifacts: Set[String],
    // documentation
    customScalaDoc: Option[String],
    documentationLinks: List[(String, String)],
    primaryTopic: Option[String] = None,
    beginnerIssuesLabel: Option[String],
    beginnerIssues: List[GithubIssue] = List(),
    selectedBeginnerIssues: List[GithubIssue] = List(),
    chatroom: Option[Url],
    contributingGuide: Option[Url],
    codeOfConduct: Option[Url]
) {
  def update(
      project: Project,
      paths: DataPaths,
      githubDownload: GithubDownload,
      fromStored: Boolean = false
  ): Project = {

    val githubWithKeywords =
      if (project.github.isEmpty) {
        Some(GithubInfo(topics = keywords))
      } else {
        project.github.map(github => github.copy(topics = github.topics ++ keywords))
      }

    val oldBeginnerIssueLabel = project.github.flatMap(_.beginnerIssuesLabel)
    val getBeginnerIssues =
      fromStored && beginnerIssues.isEmpty && beginnerIssuesLabel.isDefined
    val newBeginnerIssues =
      if (getBeginnerIssues) {
        githubDownload.runBeginnerIssues(
          project.githubRepo,
          beginnerIssuesLabel.getOrElse("")
        )
        GithubReader
          .beginnerIssues(paths, project.githubRepo)
          .getOrElse(List())
      } else beginnerIssues

    val newChatroom = chatroom.filterNot(_.target == "")
    val newContributingGuide = contributingGuide.filterNot(_.target == "")
    val newCodeOfConduct = codeOfConduct.filterNot(_.target == "")

    project.copy(
      contributorsWanted = contributorsWanted,
      defaultArtifact =
        if (defaultArtifact.isDefined) defaultArtifact
        else project.defaultArtifact,
      defaultStableVersion = defaultStableVersion,
      strictVersions = strictVersions,
      deprecated = deprecated,
      artifactDeprecations = artifactDeprecations,
      cliArtifacts = cliArtifacts,
      hasCli = cliArtifacts.nonEmpty,
      github = githubWithKeywords.map(github =>
        github.copy(
          beginnerIssuesLabel = beginnerIssuesLabel.filterNot(_ == ""),
          beginnerIssues = newBeginnerIssues,
          selectedBeginnerIssues = selectedBeginnerIssues,
          // default to project's chatroom/contributingGuide/codeOfConduct
          // if updating from stored project and stored project didn't override
          // that value
          chatroom =
            if (fromStored && !newChatroom.isDefined)
              project.github.flatMap(_.chatroom)
            else newChatroom,
          contributingGuide =
            if (fromStored && !newContributingGuide.isDefined)
              project.github.flatMap(_.contributingGuide)
            else newContributingGuide,
          codeOfConduct =
            if (fromStored && !newCodeOfConduct.isDefined)
              project.github.flatMap(_.codeOfConduct)
            else newCodeOfConduct
        )
      ),
      customScalaDoc = customScalaDoc.filterNot(_ == ""),
      documentationLinks = documentationLinks.filterNot {
        case (label, link) =>
          label == "" || link == ""
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
      strictVersions,
      deprecated,
      artifactDeprecations,
      cliArtifacts,
      customScalaDoc,
      documentationLinks,
      primaryTopic,
      github.flatMap(_.beginnerIssuesLabel),
      github.map(_.beginnerIssues).getOrElse(List()),
      github.map(_.selectedBeginnerIssues).getOrElse(List()),
      github.flatMap(_.chatroom),
      github.flatMap(_.contributingGuide),
      github.flatMap(_.codeOfConduct)
    )
  }
}
