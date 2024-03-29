package scaladex.infra.migrations

import scala.concurrent.ExecutionContext

import cats.effect.ContextShift
import cats.effect.IO
import doobie.implicits._
import doobie.util.transactor.Transactor
import scaladex.infra.config.PostgreSQLConfig

trait ScaladexBaseMigration {
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  private val config = PostgreSQLConfig.load().get
  val xa: Transactor.Aux[IO, Unit] =
    Transactor
      .fromDriverManager[IO](
        config.driver,
        config.url,
        config.user,
        config.pass.decode
      )

  def run[A](xa: doobie.Transactor[IO])(v: doobie.ConnectionIO[A]): IO[A] =
    v.transact(xa)
}
