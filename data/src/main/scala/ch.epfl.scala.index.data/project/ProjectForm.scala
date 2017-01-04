package ch.epfl.scala.index
package data
package project

import model.Project

case class ProjectForm(
    // project
    contributorsWanted: Boolean = false,
    keywords: Set[String] = Set(),
    defaultStableVersion: Boolean = true,
    deprecated: Boolean = false,
    artifactDeprecations: Set[String] = Set(),
    cliArtifacts: Set[String] = Set(),
    // documentation
    customScalaDoc: Option[String] = None,
    documentationLinks: List[(String, String)] = List()
) {
  def update(project: Project, live: Boolean = true): Project = {
    project.copy(
      contributorsWanted = contributorsWanted,
      keywords = keywords,
      defaultStableVersion = defaultStableVersion,
      deprecated = deprecated,
      artifactDeprecations = artifactDeprecations,
      cliArtifacts = cliArtifacts,
      hasCli = !cliArtifacts.isEmpty,
      // documentation
      customScalaDoc = customScalaDoc.filterNot(_ == ""),
      documentationLinks = documentationLinks.filterNot { case (_, link) => link == "" },
      liveData = live
    )
  }
}

object ProjectForm {
  def apply(project: Project): ProjectForm = {
    import project._
    new ProjectForm(
      contributorsWanted,
      keywords,
      defaultStableVersion,
      deprecated,
      artifactDeprecations,
      cliArtifacts,
      customScalaDoc,
      documentationLinks
    )
  }
}
