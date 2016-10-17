package ch.epfl.scala.index
package server
package routes
package api
package impl

// import data.elastic._

import akka.actor.{Actor, ActorSystem}
import akka.stream.ActorMaterializer

import scala.concurrent.Await
import scala.concurrent.duration._

class PublishActor(dataRepository: DataRepository,
    implicit val system: ActorSystem, 
    implicit val materializer: ActorMaterializer) extends Actor {


  private val publishProcess = new impl.PublishProcess(dataRepository)


  def receive = {
    case publishData: PublishData => {
      
      if (publishData.isPom) {
        Await.result(publishProcess.writeFiles(publishData), 10.seconds)
      }

      println("block")
      blockUntilGreen()
      println("unblock")
      // Thread.sleep(2000)

      sender.!(())
    }
  }
}