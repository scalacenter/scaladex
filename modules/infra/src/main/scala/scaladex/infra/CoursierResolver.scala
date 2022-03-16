package scaladex.infra

import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import com.typesafe.scalalogging.LazyLogging
import coursier.Dependency
import coursier.Fetch
import coursier.Module
import coursier.ModuleName
import coursier.Organization
import coursier.Repositories
import coursier.cache.Cache
import coursier.core.Type
import coursier.error.ResolutionError
import scaladex.core.service.PomResolver

class CoursierResolver()(implicit val ec: ExecutionContext) extends PomResolver with LazyLogging {
  Cache.default
  private val repositories = Seq(
    Repositories.central,
    Repositories.jcenter,
    Repositories.sbtPlugin("releases")
  )
  private val fetchPoms = Fetch()
    .withArtifactTypes(Set(Type.pom))
    .withRepositories(repositories)

  def resolve(groupId: String, artifactId: String, version: String): Future[Option[Path]] = {
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
            if (count < 10) {
              Thread.sleep(10)
              retry(count + 1)
            } else Future.failed(cause)
          case NonFatal(_) => Future(None)
        }

    retry(0)
  }

  private def isConcurrentDownload(cause: ResolutionError.CantDownloadModule): Boolean =
    cause.getMessage.contains("concurrent download")
}
