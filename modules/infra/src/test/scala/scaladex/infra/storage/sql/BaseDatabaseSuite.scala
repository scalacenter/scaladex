package scaladex.infra.storage.sql

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import cats.effect.ContextShift
import cats.effect.IO
import doobie.scalatest.IOChecker
import doobie.util.transactor.Transactor
import org.scalatest.Assertions
import org.scalatest.BeforeAndAfterEach
import org.scalatest.Suite
import scaladex.infra.storage.sql.DatabaseConfig
import scaladex.infra.storage.sql.SqlDatabase

trait BaseDatabaseSuite extends IOChecker with BeforeAndAfterEach {
  self: Assertions with Suite =>

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  private val config: DatabaseConfig.PostgreSQL = DatabaseConfig
    .load()
    .get
    .asInstanceOf[DatabaseConfig.PostgreSQL]

  override val transactor: Transactor.Aux[IO, Unit] =
    Transactor
      .fromDriverManager[IO](
        config.driver,
        config.url,
        config.user,
        config.pass.decode
      )

  lazy val database = new SqlDatabase(config, transactor)

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
