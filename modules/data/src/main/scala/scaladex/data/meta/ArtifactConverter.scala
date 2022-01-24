package scaladex.data.meta

import java.time.Instant

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.Project
import scaladex.core.model.Resolver
import scaladex.core.model.SemanticVersion
import scaladex.core.model.data.LocalRepository
import scaladex.data.bintray._
import scaladex.data.cleanup._
import scaladex.data.maven.ArtifactModel
import scaladex.infra.storage.DataPaths

class ArtifactConverter(paths: DataPaths) extends BintrayProtocol with LazyLogging {
  private val artifactMetaExtractor = new ArtifactMetaExtractor(paths)
  private val pomMetaExtractor = new PomMetaExtractor(paths)
  private val githubRepoExtractor = new GithubRepoExtractor(paths)
  private val licenseCleanup = new LicenseCleanup(paths)

  def convert(pom: ArtifactModel, repo: LocalRepository, sha: String): Option[(Artifact, Seq[ArtifactDependency])] =
    for {
      pomMeta <- pomMetaExtractor.extract(pom, None, repo, sha)
      repo <- githubRepoExtractor.extract(pom)
      converted <- convert(pom, repo, pomMeta.creationDate, pomMeta.resolver)
    } yield converted

  def convert(
      pom: ArtifactModel,
      projectRef: Project.Reference,
      creationDate: Option[Instant],
      resolver: Option[Resolver] = None
  ): Option[(Artifact, Seq[ArtifactDependency])] =
    for {
      version <- SemanticVersion.tryParse(pom.version)
      artifactMeta <- artifactMetaExtractor.extract(pom)
    } yield {
      val artifact = Artifact(
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
      (artifact, dependencies)
    }
}
