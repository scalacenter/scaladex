package ch.epfl.scala.index.model

import ch.epfl.scala.index.model.misc.GithubIssue
import ch.epfl.scala.index.model.misc.Url
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewProject.DocumentationLink
import ch.epfl.scala.index.newModel.NewRelease

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
  def toUserDataForm(): NewProject.DataForm = {
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
      primaryTopic = primaryTopic
    )

  }
}
