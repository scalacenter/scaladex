package scaladex.data.meta

import java.time.Instant

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.Project
import scaladex.core.model.Resolver
import scaladex.core.model.SemanticVersion
import scaladex.data.bintray._
import scaladex.data.cleanup._
import scaladex.data.maven.ReleaseModel
import scaladex.infra.storage.DataPaths
import scaladex.infra.storage.LocalRepository

class ReleaseConverter(paths: DataPaths) extends BintrayProtocol with LazyLogging {
  private val artifactMetaExtractor = new ArtifactMetaExtractor(paths)
  private val pomMetaExtractor = new PomMetaExtractor(paths)
  private val githubRepoExtractor = new GithubRepoExtractor(paths)
  private val licenseCleanup = new LicenseCleanup(paths)

  def convert(pom: ReleaseModel, repo: LocalRepository, sha: String): Option[(Artifact, Seq[ArtifactDependency])] =
    for {
      pomMeta <- pomMetaExtractor.extract(pom, None, repo, sha)
      repo <- githubRepoExtractor.extract(pom)
      converted <- convert(pom, repo, sha, pomMeta.creationDate, pomMeta.resolver)
    } yield converted

  def convert(
      pom: ReleaseModel,
      projectRef: Project.Reference,
      sha: String,
      creationDate: Option[Instant],
      resolver: Option[Resolver] = None
  ): Option[(Artifact, Seq[ArtifactDependency])] =
    for {
      version <- SemanticVersion.tryParse(pom.version)
      artifactMeta <- artifactMetaExtractor.extract(pom)
    } yield {
      val release = Artifact(
        Artifact.GroupId(pom.groupId),
        pom.artifactId,
        version,
        Artifact.Name(artifactMeta.artifactName),
        artifactMeta.platform,
        projectRef,
        pom.description,
        creationDate,
        resolver,
        licenseCleanup(pom),
        artifactMeta.isNonStandard
      )
      val dependencies = pom.dependencies.map { dep =>
        ArtifactDependency(
          pom.mavenRef,
          dep.mavenRef,
          dep.scope.getOrElse("compile")
        )
      }.distinct
      (release, dependencies)
    }
}
