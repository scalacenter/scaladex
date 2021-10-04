package ch.epfl.scala.services

import java.util.Timer
import scala.concurrent.{Await, ExecutionContext}
import java.util.concurrent.ScheduledExecutorService
import akka.actor.{Actor, ActorSystem, Cancellable, Props, Scheduler}
import com.typesafe.scalalogging.LazyLogging

import java.lang.Runnable
import scala.collection.mutable
import scala.concurrent.duration.Duration

class SchedulerService(db: DatabaseApi) extends LazyLogging {
  implicit val ec = ExecutionContext.global // should be a fixed thread pool
  private val schedulers =  mutable.ListBuffer[MyCancellable]()
  private val name = "scaladex-scheduler"

  def start(): MyCancellable = {
    schedulers.find(_.name == name).getOrElse {
      val newOne = Scheduler().run(runnable)
      schedulers += newOne
      newOne
    }
  }

  def stop(): Unit = {
    schedulers.find(_.name == name).map{c =>
      c.cancel
      schedulers -= c
    }.getOrElse(())
  }


  private def runnable = new Runnable {
    override def run(): Unit = {
      val future =
        for {
      first <- db.countReleases()
        _ = logger.info(s"hello first $first")
      } yield ()
      Await.result(future, Duration.Inf)
    }
  }

}

// api scheuler/start
// au demarrage cest demaré
// scheduler/stop
// route avec le Cancallble , et quand tu fais stop
// SchedulerRoute(scheduler)