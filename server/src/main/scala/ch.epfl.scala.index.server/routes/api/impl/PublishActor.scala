package ch.epfl.scala.index
package server
package routes
package api
package impl

import data.DataPaths

import akka.actor.{Actor, ActorSystem}
import akka.stream.ActorMaterializer

import scala.concurrent.Await
import scala.concurrent.duration._

class PublishActor(paths: DataPaths,
                   dataRepository: DataRepository,
                   implicit val system: ActorSystem,
                   implicit val materializer: ActorMaterializer)
    extends Actor {

  private val publishProcess = new impl.PublishProcess(paths, dataRepository)

  def receive = {
    case publishData: PublishData => {
        sender ! Await.result(publishProcess.writeFiles(publishData), 10.seconds)
    }
  }
}
