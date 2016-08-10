package ch.epfl.scala.index
package data
package project

import model.Project

case class ProjectForm(
  // project
  contributorsWanted: Boolean = false,
  keywords: Set[String] = Set(),
  defaultArtifact: Option[String] = None,
  defaultStableVersion: Boolean = true,
  deprecated: Boolean = false,
  artifactDeprecations: Set[String] = Set(),

  // documentation
  customScalaDoc: Option[String] = None,
  documentationLinks: List[String] = List()
) {
  def update(project: Project, live: Boolean = true): Project = {
    project.copy(
      contributorsWanted = contributorsWanted,
      keywords = keywords,
      defaultArtifact = 
        if(!defaultArtifact.isEmpty) defaultArtifact 
        else project.defaultArtifact,
      defaultStableVersion = defaultStableVersion,
      deprecated = deprecated,
      artifactDeprecations = artifactDeprecations,

      // documentation
      customScalaDoc = customScalaDoc.filterNot(_ == ""),
      documentationLinks = documentationLinks.filterNot(_ == ""),

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
      defaultArtifact,
      defaultStableVersion,
      deprecated,
      artifactDeprecations,
      customScalaDoc,
      documentationLinks
    )
  }
}