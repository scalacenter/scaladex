package ch.epfl.scala.index.model

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubIssue
import ch.epfl.scala.index.model.misc.Url
import ch.epfl.scala.index.newModel.{NewGithubInfo, NewProject, NewRelease}
import ch.epfl.scala.index.newModel.NewProject.DocumentationLink

case class ProjectForm(
    // project
    contributorsWanted: Boolean,
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
  def toUserDataForm(): NewProject.DataForm =
    NewProject.DataForm(
      defaultStableVersion = defaultStableVersion,
      defaultArtifact = defaultArtifact.map(NewRelease.ArtifactName),
      strictVersions = strictVersions,
      customScalaDoc = customScalaDoc.filterNot(_ == ""),
      documentationLinks = documentationLinks.flatMap { case (label, link) =>
        DocumentationLink.from(label, link)
      },
      deprecated = deprecated,
      contributorsWanted = contributorsWanted,
      artifactDeprecations = artifactDeprecations.map(NewRelease.ArtifactName),
      cliArtifacts = cliArtifacts.map(NewRelease.ArtifactName),
      primaryTopic = primaryTopic,
      beginnerIssuesLabel = beginnerIssuesLabel ,
      beginnerIssues =beginnerIssues,
      selectedBeginnerIssues = selectedBeginnerIssues,
      filteredBeginnerIssues = Nil // TODO: compute this
    )

  def update(
      project: Project,
      fromStored: Boolean = false
  ): Project = {

    val getBeginnerIssues =
      fromStored && beginnerIssues.isEmpty && beginnerIssuesLabel.isDefined
    val newBeginnerIssues =
      // TODO: Update github beginner issues
//      if (getBeginnerIssues) {
//        runBeginnerIssues(
//          project.githubRepo,
//          beginnerIssuesLabel.getOrElse("")
//        )
//        GithubReader
//          .beginnerIssues(paths, project.githubRepo)
//          .getOrElse(List())
//      } else
      beginnerIssues

    val newChatroom = chatroom.filterNot(_.target == "")
    val newContributingGuide =
      contributingGuide.filterNot(_.target == "")
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
      github = Some(GithubInfo.empty.copy(
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
      documentationLinks = documentationLinks.filterNot { case (label, link) =>
        label == "" || link == ""
      },
      primaryTopic = primaryTopic
    )
  }
}
