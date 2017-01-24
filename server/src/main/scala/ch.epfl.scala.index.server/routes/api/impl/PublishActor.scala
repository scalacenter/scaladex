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

import akka.http.scaladsl.model.StatusCodes._

class PublishActor(paths: DataPaths,
                   dataRepository: DataRepository,
                   implicit val system: ActorSystem,
                   implicit val materializer: ActorMaterializer)
    extends Actor {

  private val publishProcess = new impl.PublishProcess(paths, dataRepository)

  def receive = {
    case publishData: PublishData => {
      if (publishData.isPom) {
        sender ! Await.result(publishProcess.writeFiles(publishData), 10.seconds)
      } else {
        if (publishData.userState.isSonatype) sender ! ((BadRequest, "Not a POM"))
        else sender ! ((OK, "ignoring")) // for sbt, ignore SHA1, etc
      }
    }
  }
}
