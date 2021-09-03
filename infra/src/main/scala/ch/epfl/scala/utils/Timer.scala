package ch.epfl.scala.utils

import java.time.Instant
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

object Timer {
  def timeAndLog[A](task: => A)(log: (FiniteDuration, A) => Unit): A = {
    val start  = Instant.now()
    val result = task

    val duration = toFiniteDuration(start, Instant.now())
    log(duration, result)
    result
  }
  private def toFiniteDuration(start: Instant, end: Instant): FiniteDuration =
    FiniteDuration(end.toEpochMilli - start.toEpochMilli, MILLISECONDS).toCoarsest
}
