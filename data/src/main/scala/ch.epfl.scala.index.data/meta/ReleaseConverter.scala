package ch.epfl.scala.index.data.meta

import java.time.Instant

import ch.epfl.scala.index.data.bintray._
import ch.epfl.scala.index.data.cleanup._
import ch.epfl.scala.index.data.maven.ReleaseModel
import ch.epfl.scala.index.model._
import ch.epfl.scala.index.model.release.Resolver
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.Project
import ch.epfl.scala.index.newModel.ReleaseDependency
import ch.epfl.scala.services.storage.DataPaths
import ch.epfl.scala.services.storage.LocalRepository
import com.typesafe.scalalogging.LazyLogging

class ReleaseConverter(paths: DataPaths) extends BintrayProtocol with LazyLogging {
  private val artifactMetaExtractor = new ArtifactMetaExtractor(paths)
  private val pomMetaExtractor = new PomMetaExtractor(paths)
  private val githubRepoExtractor = new GithubRepoExtractor(paths)
  private val licenseCleanup = new LicenseCleanup(paths)

  def convert(pom: ReleaseModel, repo: LocalRepository, sha: String): Option[(NewRelease, Seq[ReleaseDependency])] =
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
  ): Option[(NewRelease, Seq[ReleaseDependency])] =
    for {
      version <- SemanticVersion.tryParse(pom.version)
      artifactMeta <- artifactMetaExtractor.extract(pom)
    } yield {
      val release = NewRelease(
        pom.mavenRef,
        version,
        projectRef.organization,
        projectRef.repository,
        NewRelease.ArtifactName(artifactMeta.artifactName),
        artifactMeta.platform,
        pom.description,
        creationDate,
        resolver,
        licenseCleanup(pom),
        artifactMeta.isNonStandard
      )
      val dependencies = pom.dependencies.map { dep =>
        ReleaseDependency(
          pom.mavenRef,
          dep.mavenRef,
          dep.scope.getOrElse("compile")
        )
      }.distinct
      (release, dependencies)
    }
}
