package ch.epfl.scala.index.server.routes

import ch.epfl.scala.index.server.GithubUserSession
import ch.epfl.scala.index.server.config.ServerConfig
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.service.SchedulerDatabase
import scaladex.core.test.InMemoryDatabase
import scaladex.infra.storage.DataPaths
import scaladex.infra.storage.local.LocalStorageRepo

trait ControllerBaseSuite extends AnyFunSpec with Matchers {
  private val config = ServerConfig.load()
  val env = config.api.env
  val githubUserSession = new GithubUserSession(config.session)

  val database: SchedulerDatabase = new InMemoryDatabase()
  val dataPaths: DataPaths = config.dataPaths
  val localStorage = new LocalStorageRepo(dataPaths)
}
