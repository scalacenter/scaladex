package ch.epfl.scala.index.server.routes.spec2

import akka.http.scaladsl.testkit.TestFrameworkInterface
import org.specs2.execute.{Failure, FailureException}

trait SpecificationTestFrameworkInterface extends TestFrameworkInterface {
  def cleanUp()

  // TODO: Refine start of stack trace by removing initial lines that are produced by the test framework
  def failTest(msg: String): Nothing = throw new FailureException(Failure(msg))
}
