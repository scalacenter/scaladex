package scaladex.core.service

import java.nio.file.Path

import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.Project

trait Storage {
  def clearProjects(): Unit
  def saveProject(project: Project, artifacts: Seq[Artifact], dependencies: Seq[ArtifactDependency]): Unit
  def loadAllProjects(): Iterator[(Project, Seq[Artifact], Seq[ArtifactDependency])]

  def createTempFile(content: String, prefix: String, suffix: String): Path
  def deleteTempFile(path: Path): Unit
}
