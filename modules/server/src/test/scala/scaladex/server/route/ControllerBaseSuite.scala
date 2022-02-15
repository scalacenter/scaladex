package scaladex.server.route

import java.nio.file.Files
import java.nio.file.Path

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.service.SearchEngine
import scaladex.core.test.InMemoryDatabase
import scaladex.core.test.InMemorySearchEngine
import scaladex.core.test.MockGithubAuth
import scaladex.infra.DataPaths
import scaladex.infra.FilesystemStorage
import scaladex.server.GithubUserSession
import scaladex.server.config.ServerConfig

trait ControllerBaseSuite extends AsyncFunSpec with Matchers with ScalatestRouteTest {
  val index: Path = Files.createTempDirectory("scaladex-index")
  val config: ServerConfig = {
    val realConfig = ServerConfig.load()
    realConfig.copy(filesystem = realConfig.filesystem.copy(index = index))
  }

  val githubUserSession = new GithubUserSession(config.session)
  val githubAuth = MockGithubAuth

  val database: InMemoryDatabase = new InMemoryDatabase()
  val searchEngine: SearchEngine = new InMemorySearchEngine()
  val dataPaths: DataPaths = DataPaths.from(config.filesystem)
  val localStorage: FilesystemStorage = FilesystemStorage(config.filesystem)
}
