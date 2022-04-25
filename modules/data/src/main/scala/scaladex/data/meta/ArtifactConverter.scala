package scaladex.data.meta

import java.time.Instant

import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.ArtifactDependency.Scope
import scaladex.core.model.Project
import scaladex.core.model.SemanticVersion
import scaladex.data.cleanup._
import scaladex.data.maven.ArtifactModel
import scaladex.infra.DataPaths

class ArtifactConverter(paths: DataPaths) {
  private val artifactMetaExtractor = new ArtifactMetaExtractor(paths)
  private val licenseCleanup = new LicenseCleanup(paths)

  def convert(
      pom: ArtifactModel,
      projectRef: Project.Reference,
      creationDate: Instant
  ): Option[(Artifact, Seq[ArtifactDependency])] =
    for {
      version <- SemanticVersion.parse(pom.version)
      artifactMeta <- artifactMetaExtractor.extract(pom)
    } yield {
      val artifact = Artifact(
        Artifact.GroupId(pom.groupId),
        pom.artifactId,
        version,
        Artifact.Name(artifactMeta.artifactName),
        projectRef,
        pom.description,
        creationDate,
        None,
        licenseCleanup(pom),
        artifactMeta.isNonStandard,
        artifactMeta.binaryVersion.platform,
        artifactMeta.binaryVersion.language
      )
      val dependencies = pom.dependencies.map { dep =>
        ArtifactDependency(
          pom.mavenRef,
          dep.mavenRef,
          dep.scope.map(Scope.apply).getOrElse(Scope.compile)
        )
      }.distinct
      (artifact, dependencies)
    }
}
