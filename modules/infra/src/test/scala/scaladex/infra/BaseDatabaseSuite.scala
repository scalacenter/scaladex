package scaladex.infra

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import scaladex.infra.config.CacheConfig
import scaladex.infra.config.PostgreSQLConfig
import scaladex.infra.sql.DoobieUtils

import cats.effect.IO
import com.zaxxer.hikari.HikariDataSource
import doobie.util.transactor.Transactor
import org.scalatest.Assertions
import org.scalatest.BeforeAndAfterEach
import org.scalatest.Suite

trait BaseDatabaseSuite extends IOChecker with BeforeAndAfterEach:
  self: Assertions with Suite =>

  private val config: PostgreSQLConfig = PostgreSQLConfig
    .load()
    .get

  private val cacheConfig: CacheConfig = CacheConfig.load()

  override val transactor: Transactor[IO] =
    Transactor
      .fromDriverManager[IO](
        config.driver,
        config.url,
        config.user,
        config.pass.decode
      )

  lazy val database = new SqlDatabase(BaseDatabaseSuite.datasource, transactor, cacheConfig)

  override def beforeEach(): Unit =
    Await.result(cleanTables(), Duration.Inf)

  private def cleanTables(): Future[Unit] =
    val reset = for
      _ <- database.dropTables
      _ <- database.migrate
    yield ()
    reset.unsafeToFuture()
end BaseDatabaseSuite

object BaseDatabaseSuite:
  private val config: PostgreSQLConfig = PostgreSQLConfig
    .load()
    .get

  val datasource: HikariDataSource = DoobieUtils.getHikariDataSource(config)
