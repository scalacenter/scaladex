package ch.epfl.scala.index
package data
package project

import model.Project
import model.misc.GithubInfo

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
    primaryTopic: Option[String] = None
) {
  def update(project: Project): Project = {

    val githubWithKeywords =
      if (project.github.isEmpty) {
        Some(GithubInfo(topics = keywords))
      } else {
        project.github.map(github =>
          github.copy(topics = github.topics ++ keywords))
      }

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
      github = githubWithKeywords,
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
      primaryTopic
    )
  }
}
