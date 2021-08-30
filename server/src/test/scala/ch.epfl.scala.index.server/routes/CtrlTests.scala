package ch.epfl.scala.index.server.routes

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Try

import cats.effect.ContextShift
import cats.effect.IO
import ch.epfl.scala.index.data.DataPaths
import ch.epfl.scala.index.server.GithubUserSession
import ch.epfl.scala.index.server.config.ServerConfig
import ch.epfl.scala.services.storage.sql.DbConf.H2
import ch.epfl.scala.services.storage.sql.SqlRepo
import doobie.Transactor
import doobie.util.transactor
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

trait CtrlTests extends AnyFunSpec with Matchers {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)
  val dbConf: H2 = H2(
    "jdbc:h2:mem:scaladex_db;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
  )
  private val xa: transactor.Transactor.Aux[IO, Unit] =
    Transactor.fromDriverManager[IO](dbConf.driver, dbConf.url, "", "")
  private val config = ServerConfig.load()
  val githubUserSession = new GithubUserSession(config.session)

  val db = new SqlRepo(dbConf, xa)

  val dataPaths: DataPaths = DataPaths(List()) //

  def insertMockData(): Unit = {
    import Values._
    db.migrate().unsafeRunSync() // create tables
    // Insert mock data
    await(db.insertRelease(release)).get
    await(db.insertProject(project)).get
  }

  def await[A](f: Future[A]): Try[A] = Try(
    Await.result(f, Duration.Inf)
  )
}
