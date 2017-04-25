package ch.epfl.scala.index
package data
package project

import model.Project

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
    documentationLinks: List[(String, String)]
) {
  def update(project: Project): Project = {
    project.copy(
      contributorsWanted = contributorsWanted,
      keywords = keywords,
      defaultArtifact =
        if (defaultArtifact.isDefined) defaultArtifact
        else project.defaultArtifact,
      defaultStableVersion = defaultStableVersion,
      deprecated = deprecated,
      artifactDeprecations = artifactDeprecations,
      cliArtifacts = cliArtifacts,
      hasCli = cliArtifacts.nonEmpty,
      // documentation
      customScalaDoc = customScalaDoc.filterNot(_ == ""),
      documentationLinks = documentationLinks.filterNot { case (_, link) => link == "" }
    )
  }
}

object ProjectForm {
  def apply(project: Project): ProjectForm = {
    import project._
    new ProjectForm(
      contributorsWanted,
      keywords,
      defaultArtifact,
      defaultStableVersion,
      deprecated,
      artifactDeprecations,
      cliArtifacts,
      customScalaDoc,
      documentationLinks
    )
  }
}
