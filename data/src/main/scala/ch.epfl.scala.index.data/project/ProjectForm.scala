package ch.epfl.scala.index
package data
package project

import model.Project

case class ProjectForm(
  // project
  contributorsWanted: Boolean = false,
  keywords: Set[String] = Set(),
  defaultArtifact: Option[String] = None,
  deprecated: Boolean = false,
  artifactDeprecations: Set[String] = Set(),

  // documentation
  customScalaDoc: Option[String] = None,
  documentationLinks: List[String] = List()
) {
  def update(project: Project): Project = {
    project.copy(
      contributorsWanted = contributorsWanted,
      keywords = keywords,
      defaultArtifact = defaultArtifact,
      deprecated = deprecated,
      artifactDeprecations = artifactDeprecations,

      // documentation
      customScalaDoc = customScalaDoc,
      documentationLinks = documentationLinks.filterNot(_ == ""),

      liveData = true
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
      deprecated,
      artifactDeprecations,
      customScalaDoc,
      documentationLinks
    )
  }
}