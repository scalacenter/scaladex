package ch.epfl.scala.index.server

import scala.concurrent.ExecutionContext

import cats.effect.ContextShift
import cats.effect.IO
import ch.epfl.scala.services.storage.sql.DbConf.H2
import ch.epfl.scala.services.storage.sql.SqlRepo
import doobie.Transactor
import doobie.util.transactor

object Values {
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)
  private val dbConf = H2(
    "jdbc:h2:mem:scaladex_db;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
  )
  val xa: transactor.Transactor.Aux[IO, Unit] =
    Transactor.fromDriverManager[IO](dbConf.driver, dbConf.url, "", "")
  val db = new SqlRepo(dbConf, xa)
}
