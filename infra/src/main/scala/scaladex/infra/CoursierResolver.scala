package scaladex.infra

import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

import com.typesafe.scalalogging.LazyLogging
import coursier.Dependency
import coursier.Fetch
import coursier.Module
import coursier.ModuleName
import coursier.Organization
import coursier.Repositories
import coursier.Repository
import coursier.cache.Cache
import coursier.core.Type
import coursier.error.ResolutionError
import scaladex.core.model.data.LocalPomRepository
import scaladex.core.model.data.LocalPomRepository._
import scaladex.core.service.PomResolver

class CoursierResolver(repositories: Seq[Repository])(implicit val ec: ExecutionContext)
    extends PomResolver
    with LazyLogging {
  private val cache = Cache.default
  private val fetchPoms = Fetch()
    .withArtifactTypes(Set(Type.pom))
    .withRepositories(repositories)

  def resolve(groupId: String, artifactId: String, version: String): Option[Path] = {
    val dep = Dependency(Module(Organization(groupId), ModuleName(artifactId)), version)

    // coursier cannot download the same file concurrently
    // retry 10 times or fail
    def retry(count: Int): Option[Path] =
      try fetchPoms.addDependencies(dep).run().headOption.map(_.toPath)
      catch {
        case cause: ResolutionError.CantDownloadModule if isConcurrentDownload(cause) =>
          logger.warn(s"Concurrent download of pom $groupId:$artifactId:$version")
          if (count < 10) {
            Thread.sleep(10)
            retry(count + 1)
          } else throw cause
        case NonFatal(_) =>
          None
      }

    retry(0)
  }

  private def isConcurrentDownload(cause: ResolutionError.CantDownloadModule): Boolean =
    cause.getMessage.contains("concurrent download")
}

object CoursierResolver {
  def apply(repository: LocalPomRepository)(implicit ec: ExecutionContext): CoursierResolver = {
    val repositories = repository match {
      case Bintray      => Seq(Repositories.jcenter, Repositories.sbtPlugin("releases"))
      case MavenCentral => Seq(Repositories.central)
      case UserProvided =>
        // unknown so we try with central, jcenter and sbt-plugin-releases
        Seq(
          Repositories.central,
          Repositories.jcenter,
          Repositories.sbtPlugin("releases")
        )
    }
    new CoursierResolver(repositories)
  }
}
