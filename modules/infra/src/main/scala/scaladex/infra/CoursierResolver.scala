package scaladex.infra

import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import scaladex.core.model.Artifact
import scaladex.core.service.PomResolver

import com.typesafe.scalalogging.LazyLogging
import coursier.Dependency
import coursier.Fetch
import coursier.Module
import coursier.ModuleName
import coursier.Organization
import coursier.Repositories
import coursier.core.Type
import coursier.error.ResolutionError
import coursier.maven.SbtMavenRepository

class CoursierResolver(using ExecutionContext) extends PomResolver with LazyLogging:
  private val repositories = Seq(
    SbtMavenRepository(Repositories.central),
    SbtMavenRepository(Repositories.jcenter),
    Repositories.sbtPlugin("releases")
  )
  private val fetchPoms = Fetch()
    .withArtifactTypes(Set(Type.pom))
    .withRepositories(repositories)

  def resolveSync(ref: Artifact.Reference): Path =
    val dep = Dependency(Module(Organization(ref.groupId.value), ModuleName(ref.artifactId.value)), ref.version.value)
      .withPublication(ref.artifactId.value, Type.pom)
    fetchPoms
      .addDependencies(dep)
      .run()
      .head
      .toPath

  def resolve(groupId: String, artifactId: String, version: String): Future[Option[Path]] =
    val dep = Dependency(Module(Organization(groupId), ModuleName(artifactId)), version)
      .withPublication(artifactId, Type.pom)

    // coursier cannot download the same file concurrently
    // retry 10 times or fail
    def retry(count: Int): Future[Option[Path]] =
      fetchPoms
        .addDependencies(dep)
        .future()
        .map(_.headOption.map(_.toPath()))
        .recoverWith {
          case cause: ResolutionError.CantDownloadModule if isConcurrentDownload(cause) =>
            logger.warn(s"Concurrent download of pom $groupId:$artifactId:$version")
            if count < 10 then
              Thread.sleep(10)
              retry(count + 1)
            else Future.failed(cause)
          case NonFatal(_) => Future(None)
        }

    retry(0)
  end resolve

  private def isConcurrentDownload(cause: ResolutionError.CantDownloadModule): Boolean =
    cause.getMessage.contains("concurrent download")
end CoursierResolver
