package scaladex.infra

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import cats.effect.IO
import com.zaxxer.hikari.HikariDataSource
import doobie.scalatest.IOChecker
import doobie.util.transactor.Transactor
import org.scalatest.BeforeAndAfterEach
import org.scalatest.Suite
import scaladex.infra.config.PostgreSQLConfig
import scaladex.infra.sql.DoobieUtils
import cats.effect.Blocker
import scala.concurrent.ExecutionContext
import cats.effect.ContextShift

trait DatabaseSuite extends IOChecker with BeforeAndAfterEach {
  self: Suite =>

  override val transactor: Transactor[IO] = DatabaseSuite.transactor

  lazy val database = new SqlDatabase(DatabaseSuite.dataSource, transactor, testMode = true)

  override def beforeEach(): Unit =
    Await.result(cleanTables(), Duration.Inf)

  private def cleanTables(): Future[Unit] = {
    val reset = for {
      _ <- database.dropTables
      _ <- database.migrate
    } yield ()
    reset.unsafeToFuture()
  }
}

object DatabaseSuite {
  private val config: PostgreSQLConfig = PostgreSQLConfig.load().get

  // The datasource for the tests is global
  // Otherwise we need to close it after running a test suite
  private val dataSource: HikariDataSource = DoobieUtils.getHikariDataSource(config)

  private val ec = ExecutionContext.global
  private implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  private val blocker = Blocker[IO].use(IO.pure).unsafeRunSync()
  private val transactor = Transactor.fromDataSource[IO](dataSource, ec, blocker)
}
