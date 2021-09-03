package ch.epfl.scala.utils

import java.time.Instant

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.MILLISECONDS

object TimerUtils {
  def timeAndLog[A](task: => A)(log: FiniteDuration => Unit): A = {
    val start = Instant.now()
    val result = task

    val duration = toFiniteDuration(start, Instant.now())
    log(duration)
    result
  }
  def toFiniteDuration(start: Instant, end: Instant): FiniteDuration =
    FiniteDuration(
      end.toEpochMilli - start.toEpochMilli,
      MILLISECONDS
    ).toCoarsest
}
