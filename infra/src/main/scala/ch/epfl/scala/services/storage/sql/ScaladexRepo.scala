package ch.epfl.scala.services.storage.sql

import cats.effect.IO
import ch.epfl.scala.index.model.Project
import ch.epfl.scala.services.ScaladexDb
import ch.epfl.scala.utils.DoobieUtils

class ScaladexRepo(conf: DbConf) extends ScaladexDb {
  private[sql] val (xa, flyway) = DoobieUtils.create(conf)
  def migrate(): IO[Unit] = IO(flyway.migrate())
  def dropTables(): IO[Unit] = IO(flyway.clean())

  override def getProject(id: String): Option[Project] = None

  def insertMockData(): IO[Unit] = {
    // We can insert some mock data here
    IO(())
  }

}
