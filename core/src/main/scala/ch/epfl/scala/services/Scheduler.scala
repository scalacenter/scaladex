package ch.epfl.scala.services

import akka.actor
import akka.actor.{ActorSystem, Cancellable}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

final case class Scheduler(name: String = "scaladex-scheduler")(implicit ec: ExecutionContext)  {
  implicit val system: ActorSystem = ActorSystem(name)
  private val scheduler: actor.Scheduler = system.scheduler

  def run(run: Runnable): MyCancellable =
    MyCancellable(name, scheduler.schedule(0.minute, 1.second, run))
}
// The goal is to be able to stop the scheduler
final case class MyCancellable(name: String, cancellable: Cancellable){
  def cancel(): Unit = cancellable.cancel()
}
