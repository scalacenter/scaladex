package scaladex.infra

import scala.concurrent.ExecutionContext

import cats.effect.ContextShift
import cats.effect.IO
import doobie.*
import doobie.implicits.*
import doobie.util.testing.AnalysisArgs
import doobie.util.testing.Analyzable
import doobie.util.testing.formatReport
import doobie.util.transactor.Transactor
import org.scalatest.Assertions

trait IOChecker:
  self: Assertions =>

  given ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  def transactor: Transactor[IO]

  def colors: doobie.util.Colors = doobie.util.Colors.Ansi

  def check[A](a: A)(using ev: Analyzable[A]): Unit =
    checkImpl(Analyzable.unpack(a))

  private def checkImpl(args: AnalysisArgs): Unit =
    val report = doobie.util.testing
      .analyze(args)
      .transact(transactor)
      .unsafeRunSync()
    if !report.succeeded then
      fail(
        formatReport(args, report, colors)
          .padLeft("  ")
          .toString
      )
  end checkImpl
end IOChecker
