package scaladex.server.route

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.SearchEngine
import scaladex.core.test.InMemoryDatabase
import scaladex.core.test.InMemorySearchEngine
import scaladex.infra.storage.DataPaths
import scaladex.infra.storage.local.LocalStorageRepo
import scaladex.server.GithubUserSession
import scaladex.server.config.ServerConfig

trait ControllerBaseSuite extends AsyncFunSpec with Matchers with ScalatestRouteTest {
  val config: ServerConfig = ServerConfig.load()
  val githubUserSession = new GithubUserSession(config.session)

  val database: SchedulerDatabase = new InMemoryDatabase()
  val searchEngine: SearchEngine = new InMemorySearchEngine()
  val dataPaths: DataPaths = config.dataPaths
  val localStorage = new LocalStorageRepo(dataPaths)
}
