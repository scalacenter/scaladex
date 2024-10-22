package scaladex.server.route

import java.nio.file.Files
import java.nio.file.Path

import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.service.ProjectService
import scaladex.core.service.SearchEngine
import scaladex.core.test.InMemoryDatabase
import scaladex.core.test.InMemorySearchEngine
import scaladex.core.test.MockGithubAuth
import scaladex.infra.DataPaths
import scaladex.infra.FilesystemStorage
import scaladex.server.config.ServerConfig
import scaladex.server.service.ArtifactService
import scaladex.server.service.SearchSynchronizer

trait ControllerBaseSuite extends AsyncFunSpec with Matchers with ScalatestRouteTest {
  val index: Path = Files.createTempDirectory("scaladex-index")
  val config: ServerConfig = {
    val realConfig = ServerConfig.load()
    realConfig.copy(filesystem = realConfig.filesystem.copy(index = index))
  }

  val githubAuth = MockGithubAuth
  val database: InMemoryDatabase = new InMemoryDatabase()
  val searchEngine: SearchEngine = new InMemorySearchEngine()

  val projectService = new ProjectService(database, searchEngine)
  val artifactService = new ArtifactService(database)
  val searchSync = new SearchSynchronizer(database, projectService, searchEngine)

  val dataPaths: DataPaths = DataPaths.from(config.filesystem)
  val localStorage: FilesystemStorage = FilesystemStorage(config.filesystem)
}
