package scaladex.core.util

import java.time.Instant

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.MILLISECONDS

object TimeUtils {
  def measure[A](task: => A): (A, FiniteDuration) = {
    val start = Instant.now()
    val result = task

    val duration = toFiniteDuration(start, Instant.now())
    (result, duration)
  }

  def toFiniteDuration(start: Instant, end: Instant): FiniteDuration =
    FiniteDuration(
      end.toEpochMilli - start.toEpochMilli,
      MILLISECONDS
    ).toCoarsest

  def percentage(duration: FiniteDuration, total: FiniteDuration): Double =
    duration / total * 100
}
