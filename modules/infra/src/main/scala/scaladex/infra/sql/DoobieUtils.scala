package scaladex.infra.sql

import scala.concurrent.ExecutionContext

import cats.effect._
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import doobie._
import doobie.hikari.HikariTransactor
import org.flywaydb.core.Flyway
import scaladex.infra.config.PostgreSQLConfig

object DoobieUtils {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  def flyway(conf: PostgreSQLConfig): Flyway = {
    val datasource = getHikariDataSource(conf)
    flyway(datasource)
  }

  def flyway(datasource: HikariDataSource): Flyway =
    Flyway
      .configure()
      .dataSource(datasource)
      .locations("migrations", "scaladex/infra/migrations")
      .load()

  def getHikariDataSource(conf: PostgreSQLConfig): HikariDataSource = {
    val config: HikariConfig = new HikariConfig()
    config.setDriverClassName(conf.driver)
    config.setJdbcUrl(conf.url)
    config.setUsername(conf.user)
    config.setPassword(conf.pass.decode)
    new HikariDataSource(config)
  }

  def transactor(datasource: HikariDataSource): Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](32) // our connect EC
      be <- Blocker[IO] // our blocking EC
    } yield Transactor.fromDataSource[IO](datasource, ce, be)

  def insertOrUpdateRequest[T: Write](
      table: String,
      insertFields: Seq[String],
      onConflictFields: Seq[String],
      updateFields: Seq[String] = Seq.empty
  ): Update[T] = {
    val insert = insertRequest(table, insertFields).sql
    val onConflictFieldsStr = onConflictFields.mkString(",")
    val action = if (updateFields.nonEmpty) updateFields.mkString(" UPDATE SET ", " = ?, ", " = ?") else "NOTHING"
    Update(s"$insert ON CONFLICT ($onConflictFieldsStr) DO $action")
  }

  def insertRequest[T: Write](table: String, fields: Seq[String]): Update[T] = {
    val fieldsStr = fields.mkString(", ")
    val valuesStr = fields.map(_ => "?").mkString(", ")
    Update(s"INSERT INTO $table ($fieldsStr) VALUES ($valuesStr)")
  }

  def updateRequest[T: Write](table: String, fields: Seq[String], keys: Seq[String]): Update[T] = {
    val fieldsStr = fields.map(f => s"$f=?").mkString(", ")
    val keysStr = keys.map(k => s"$k=?").mkString(" AND ")
    Update(s"UPDATE $table SET $fieldsStr WHERE $keysStr")
  }

  def updateRequest0[T: Write](table: String, set: Seq[String], where: Seq[String]): Update[T] = {
    val setStr = set.mkString(", ")
    val whereStr = where.mkString(" AND ")
    Update(s"UPDATE $table SET $setStr WHERE $whereStr")
  }

  def selectRequest[A: Read](table: String, fields: Seq[String]): Query0[A] = {
    val fieldsStr = fields.mkString(", ")
    Query0(s"SELECT $fieldsStr FROM $table")
  }

  def selectRequest[A: Write, B: Read](table: String, fields: Seq[String], keys: Seq[String]): Query[A, B] = {
    val fieldsStr = fields.mkString(", ")
    val keysStr = keys.map(k => s"$k=?").mkString(" AND ")
    Query(s"SELECT $fieldsStr FROM $table WHERE $keysStr")
  }

  def selectRequest[A: Read](
      table: String,
      fields: Seq[String],
      where: Seq[String] = Seq.empty,
      groupBy: Seq[String] = Seq.empty,
      orderBy: Option[String] = None,
      limit: Option[Long] = None
  ): Query0[A] = {
    val fieldsStr = fields.mkString(", ")
    val whereStr = if (where.nonEmpty) where.mkString(" WHERE ", " AND ", "") else ""
    val groupByStr = if (groupBy.nonEmpty) groupBy.mkString(" GROUP BY ", ", ", "") else ""
    val orderByStr = orderBy.map(o => s" ORDER BY $o").getOrElse("")
    val limitStr = limit.map(l => s" LIMIT $l").getOrElse("")
    Query0(s"SELECT $fieldsStr FROM $table" + whereStr + groupByStr + orderByStr + limitStr)
  }

  def selectRequest1[A: Write, B: Read](
      table: String,
      fields: Seq[String],
      keys: Seq[String] = Seq.empty,
      where: Seq[String] = Seq.empty,
      groupBy: Seq[String] = Seq.empty,
      orderBy: Option[String] = None,
      limit: Option[Long] = None
  ): Query[A, B] = {
    val fieldsStr = fields.mkString(", ")
    val allWhere = keys.map(k => s"$k=?") ++ where
    val whereStr = if (allWhere.nonEmpty) allWhere.mkString(" WHERE ", " AND ", "") else ""
    val groupByStr = if (groupBy.nonEmpty) groupBy.mkString(" GROUP BY ", ", ", "") else ""
    val orderByStr = orderBy.map(o => s" ORDER BY $o").getOrElse("")
    val limitStr = limit.map(l => s" LIMIT $l").getOrElse("")
    Query(s"SELECT $fieldsStr FROM $table" + whereStr + groupByStr + orderByStr + limitStr)
  }

  def deleteRequest[T: Write](table: String, where: Seq[String]): Update[T] = {
    val whereK = where.map(k => s"$k=?").mkString(" AND ")
    Update(s"DELETE FROM $table WHERE $whereK")
  }
}
