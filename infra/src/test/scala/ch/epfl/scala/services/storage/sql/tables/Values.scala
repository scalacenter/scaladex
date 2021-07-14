package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.services.storage.sql.DbConf.H2
import ch.epfl.scala.services.storage.sql.SqlRepo

object Values {
  val dbConf = H2(
    "jdbc:h2:mem:scaladex_db;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
  )
  val db = new SqlRepo(dbConf)
}
