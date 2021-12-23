package scaladex.template

import java.time.Instant

import scala.concurrent.duration.FiniteDuration

sealed trait SchedulerStatus {
  val name: String
  val when: Instant
  val lastRunAt: Option[Instant]
  val durationOfLastRun: Option[FiniteDuration]

  val status: String = this match {
    case _: SchedulerStatus.Created => "Created"
    case _: SchedulerStatus.Started => "Started"
    case _: SchedulerStatus.Stopped => "Stopped"
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
  case class Created(name: String, when: Instant) extends SchedulerStatus {
    val lastRunAt: Option[Instant] = None
    val durationOfLastRun: Option[FiniteDuration] = None
  }
  case class Started(
      name: String,
      when: Instant,
      running: Boolean,
      lastRunAt: Option[Instant],
      durationOfLastRun: Option[FiniteDuration]
  ) extends SchedulerStatus
  case class Stopped(name: String, when: Instant) extends SchedulerStatus {
    val lastRunAt: Option[Instant] = None
    val durationOfLastRun: Option[FiniteDuration] = None
  }
}
