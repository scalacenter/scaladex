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
import scaladex.infra.storage.sql.SqlRepo

trait BaseDatabaseSuite extends IOChecker with BeforeAndAfterEach {
  self: Assertions with Suite =>

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  private val dbConf: DatabaseConfig.PostgreSQL = DatabaseConfig
    .load()
    .get
    .asInstanceOf[DatabaseConfig.PostgreSQL]

  override val transactor: Transactor.Aux[IO, Unit] =
    Transactor
      .fromDriverManager[IO](
        dbConf.driver,
        dbConf.url,
        dbConf.user,
        dbConf.pass.decode
      )

  lazy val db = new SqlRepo(dbConf, transactor)

  override def beforeEach(): Unit =
    Await.result(cleanTables(), Duration.Inf)

  private def cleanTables(): Future[Unit] = {
    val reset = for {
      _ <- db.dropTables
      _ <- db.migrate
    } yield ()
    reset.unsafeToFuture()
  }
}
