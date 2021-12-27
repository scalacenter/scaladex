package ch.epfl.scala.index.server.routes

import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.scala.index.server.GithubUserSession
import ch.epfl.scala.index.server.config.ServerConfig
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.SearchEngine
import scaladex.core.test.InMemoryDatabase
import scaladex.core.test.InMemorySearchEngine
import scaladex.infra.storage.DataPaths
import scaladex.infra.storage.local.LocalStorageRepo

trait ControllerBaseSuite extends AnyFunSpec with Matchers with ScalatestRouteTest {
  private val config = ServerConfig.load()
  val env = config.api.env
  val githubUserSession = new GithubUserSession(config.session)

  val database: SchedulerDatabase = new InMemoryDatabase()
  val searchEngine: SearchEngine = new InMemorySearchEngine()
  val dataPaths: DataPaths = config.dataPaths
  val localStorage = new LocalStorageRepo(dataPaths)
}
