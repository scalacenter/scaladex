package scaladex.server.route

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.Version
import scaladex.core.service.MavenCentralClient
import scaladex.core.service.ProjectService
import scaladex.core.service.SearchEngine
import scaladex.core.test.InMemoryDatabase
import scaladex.core.test.InMemorySearchEngine
import scaladex.core.test.MockGithubAuth
import scaladex.infra.DataPaths
import scaladex.infra.FilesystemStorage
import scaladex.server.config.ServerConfig
import scaladex.server.service.ArtifactService
import scaladex.server.service.ProjectSettingsService
import scaladex.server.service.ScaladocService
import scaladex.server.service.SearchSynchronizer

import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

private object NoopMavenCentralClient extends MavenCentralClient:
  def getAllArtifactIds(groupId: Artifact.GroupId): Future[Seq[Artifact.ArtifactId]] = Future.successful(Seq.empty)
  def getAllVersions(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): Future[Seq[Version]] =
    Future.successful(Seq.empty)
  def getPomFile(ref: Artifact.Reference): Future[Option[(String, Instant)]] = Future.successful(None)
  def getJavadocJar(ref: Artifact.Reference): Future[Option[Array[Byte]]] = Future.successful(None)

trait ControllerBaseSuite extends AsyncFunSpec with Matchers with ScalatestRouteTest:
  val index: Path = Files.createTempDirectory("scaladex-index")
  val scaladoc: Path = Files.createTempDirectory("scaladex-scaladoc")
  val config: ServerConfig =
    val realConfig = ServerConfig.load()
    realConfig.copy(filesystem = realConfig.filesystem.copy(index = index, scaladoc = scaladoc))

  val githubAuth = MockGithubAuth
  val database: InMemoryDatabase = new InMemoryDatabase()
  val searchEngine: SearchEngine = new InMemorySearchEngine()

  val projectService = new ProjectService(database, searchEngine)
  val artifactService = new ArtifactService(database)
  val searchSync = new SearchSynchronizer(database, projectService, searchEngine)
  val settingsService = new ProjectSettingsService(database, projectService, artifactService, searchEngine)

  val dataPaths: DataPaths = DataPaths.from(config.filesystem)
  val localStorage: FilesystemStorage = FilesystemStorage(config.filesystem)
  val scaladocService: ScaladocService = ScaladocService(config.filesystem.scaladoc, NoopMavenCentralClient)
end ControllerBaseSuite
