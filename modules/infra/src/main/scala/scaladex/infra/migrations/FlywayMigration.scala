package scaladex.infra.migrations

import scala.concurrent.ExecutionContext

import cats.effect.ContextShift
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import scaladex.infra.config.PostgreSQLConfig

abstract class FlywayMigration extends BaseJavaMigration with LazyLogging {
  def migrationIO: IO[Unit]

  def run[A](v: doobie.ConnectionIO[A]): IO[A] =
    v.transact(FlywayMigration.transactor)

  override def migrate(context: Context): Unit =
    migrationIO.unsafeRunSync()
}

object FlywayMigration {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private val config = PostgreSQLConfig.load().get
  private val transactor: Transactor.Aux[IO, Unit] =
    Transactor.fromDriverManager[IO](config.driver, config.url, config.user, config.pass.decode)
}
