package scaladex.server.route.api
package impl

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.Actor
import akka.actor.ActorSystem
import scaladex.core.service.WebDatabase
import scaladex.infra.storage.DataPaths

class PublishActor(
    paths: DataPaths,
    db: WebDatabase,
    implicit val system: ActorSystem
) extends Actor {

  private val publishProcess =
    new impl.PublishProcess(paths, db)

  def receive: PartialFunction[Any, Unit] = {
    case publishData: PublishData =>
      // TODO be non-blocking, by stashing incoming messages until
      // the publish process has completed
      sender() ! Await.result(publishProcess.writeFiles(publishData), 1.minute)
  }
}
