package ch.epfl.scala.index
package server
package routes
package api
package impl

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.Actor
import akka.actor.ActorSystem
import ch.epfl.scala.index.data.DataPaths
import ch.epfl.scala.index.search.DataRepository

class PublishActor(
    paths: DataPaths,
    dataRepository: DataRepository,
    implicit val system: ActorSystem
) extends Actor {

  private val publishProcess = new impl.PublishProcess(paths, dataRepository)

  def receive: PartialFunction[Any, Unit] = {
    case publishData: PublishData => {
      // TODO be non-blocking, by stashing incoming messages until
      // the publish process has completed
      sender() ! Await.result(publishProcess.writeFiles(publishData), 1.minute)
    }
  }
}
