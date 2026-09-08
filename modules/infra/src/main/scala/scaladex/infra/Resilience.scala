package scaladex.infra

import scala.util.control.NonFatal

import org.apache.pekko.pattern.CircuitBreakerOpenException

/** Error-recovery policy for batch jobs backed by HTTP clients. Use with `Future.recover`: an individual item is
  * recovered from any non-fatal failure, while an open circuit breaker (and fatal errors) is left unhandled so it
  * propagates and aborts the whole job.
  */
object Resilience:
  def tolerate[B](fallback: Throwable => B): PartialFunction[Throwable, B] =
    case error if isTolerable(error) => fallback(error)

  private def isTolerable(error: Throwable): Boolean = error match
    case _: CircuitBreakerOpenException => false
    case NonFatal(_) => true
    case _ => false
