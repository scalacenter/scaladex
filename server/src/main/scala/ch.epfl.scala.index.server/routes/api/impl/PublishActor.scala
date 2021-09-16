package ch.epfl.scala.index
package server
package routes
package api
package impl

import ch.epfl.scala.index.data.DataPaths
import ch.epfl.scala.index.search.ESRepo
import ch.epfl.scala.services.DatabaseApi
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.StashBuffer
import akka.actor.typed.scaladsl.ActorContext

object PublishActor {

  def apply(
      paths: DataPaths,
      dataRepository: ESRepo,
      db: DatabaseApi
  ): Behavior[PublishData] =
    Behaviors.setup { (ac: ActorContext[PublishData]) =>
      val publishProcess = new impl.PublishProcess(paths, dataRepository, db)(
        ac.system.classicSystem
      )
      import ac.executionContext

      def ready: Behavior[PublishData] = Behaviors.receiveMessage {
        (pd: PublishData) =>
          val f = publishProcess.writeFiles(pd).map(pd.requester ! _)
          // After starting the write, the actor puts all messages in stash
          Behaviors.withStash(Int.MaxValue) {
            (stash: StashBuffer[PublishData]) =>
              Behaviors.receiveMessage { (newMessage: PublishData) =>
                stash.stash(newMessage)
                // if future is over, we process next message in stash
                if (f.isCompleted) stash.unstashAll(ready)
                // Otherwise the new message is stashed and we keep waiting
                else Behaviors.same
              }
          }
      }
      ready
    }
}
