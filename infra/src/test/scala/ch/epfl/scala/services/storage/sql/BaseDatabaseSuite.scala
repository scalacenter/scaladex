package ch.epfl.scala.services.storage.sql

import scala.concurrent.ExecutionContext

import cats.effect.ContextShift
import cats.effect.IO
import doobie.scalatest.IOChecker
import doobie.util.transactor.Transactor
import org.scalatest.Assertions
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite

trait BaseDatabaseSuite extends IOChecker with BeforeAndAfterAll {
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

  override def beforeAll(): Unit =
    cleanTables()

  def cleanTables(): Unit = {
    val reset = for {
      _ <- db.dropTables
      _ <- db.migrate
    } yield ()
    reset.unsafeRunSync()
  }

  override def afterAll(): Unit = db.dropTables.unsafeRunSync()
}
