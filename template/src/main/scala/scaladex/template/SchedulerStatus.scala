package scaladex.template

import java.time.Instant

sealed trait SchedulerStatus {
  val name: String
  val when: Instant
  val value: String = this match {
    case SchedulerStatus.Created(_, _) => "Created"
    case SchedulerStatus.Running(_, _) => "Running"
    case SchedulerStatus.Stopped(_, _) => "Stopped"
  }
  def isRunning(): Boolean = this match {
    case SchedulerStatus.Running(_, _) => true
    case _ => false
  }
}
object SchedulerStatus {
  case class Created(name: String, when: Instant) extends SchedulerStatus
  case class Running(name: String, when: Instant) extends SchedulerStatus
  case class Stopped(name: String, when: Instant) extends SchedulerStatus
}
