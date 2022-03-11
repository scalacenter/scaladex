package scaladex.view

import java.time.Instant

sealed trait AdminTaskStatus {
  val name: String
  val createdBy: String
  val status: String = this match {
    case AdminTaskStatus.Created(_, _, _)         => "Created"
    case AdminTaskStatus.Started(_, _, _, _)      => "Started"
    case AdminTaskStatus.Succeeded(_, _, _, _, _) => "Succeeded"
    case AdminTaskStatus.Failed(_, _, _, _, _, _) => "Failed"
  }
  val created: Instant
  val startedTime: Option[Instant] = this match {
    case AdminTaskStatus.Created(_, _, _)               => None
    case AdminTaskStatus.Started(_, _, _, started)      => Some(started)
    case AdminTaskStatus.Succeeded(_, _, _, started, _) => Some(started)
    case AdminTaskStatus.Failed(_, _, _, started, _, _) => Some(started)
  }
  val succeededTime: Option[Instant] = this match {
    case AdminTaskStatus.Succeeded(_, _, _, _, succeeded) => Some(succeeded)
    case _                                                => None
  }
  val failedTimeWithErrorMessage: Option[(Instant, String)] = this match {
    case AdminTaskStatus.Failed(_, _, _, _, failed, errorMessage) => Some((failed, errorMessage))
    case _                                                        => None
  }

}

object AdminTaskStatus {
  case class Created(name: String, createdBy: String, created: Instant) extends AdminTaskStatus {
    def start(when: Instant): Started = Started(name, createdBy, created, when)
  }
  case class Started(name: String, createdBy: String, created: Instant, started: Instant) extends AdminTaskStatus {
    def success(when: Instant): Succeeded = Succeeded(name, createdBy, created, started, when)
    def failed(when: Instant, errorMessage: String): Failed =
      Failed(name, createdBy, created, started, when, errorMessage)
  }
  case class Succeeded(name: String, createdBy: String, created: Instant, started: Instant, succeeded: Instant)
      extends AdminTaskStatus
  case class Failed(
      name: String,
      createdBy: String,
      created: Instant,
      started: Instant,
      Failed: Instant,
      errorMessage: String
  ) extends AdminTaskStatus
}
