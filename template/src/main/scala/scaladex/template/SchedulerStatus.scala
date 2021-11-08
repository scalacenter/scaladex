package scaladex.template

import java.time.Instant

import scala.concurrent.duration.FiniteDuration

sealed trait SchedulerStatus {
  val when: Instant
  val value: String = this match {
    case SchedulerStatus.Created(_)              => "Created"
    case s: SchedulerStatus.Started if s.running => "Running"
    case s: SchedulerStatus.Started              => "Started"
    case SchedulerStatus.Stopped(_)              => "Stopped"
  }
  def isRunning(): Boolean = this match {
    case s: SchedulerStatus.Started if s.running => true
    case _                                       => false
  }
  def isStarted(): Boolean = this match {
    case s: SchedulerStatus.Started => true
    case _                          => false
  }
}
object SchedulerStatus {
  case class Created(when: Instant) extends SchedulerStatus
  case class Started(
      when: Instant,
      running: Boolean,
      triggeredWhen: Option[Instant],
      durationOfLastRun: Option[FiniteDuration]
  ) extends SchedulerStatus
  case class Stopped(when: Instant) extends SchedulerStatus
}
