package scaladex.infra

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.QueueOfferResult
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.scaladsl.SourceQueueWithComplete
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport

abstract class CommonAkkaHttpClient(implicit system: ActorSystem) extends FailFastCirceSupport {

  val poolClientFlow: Flow[
    (HttpRequest, Promise[HttpResponse]),
    (Try[HttpResponse], Promise[HttpResponse]),
    Http.HostConnectionPool
  ]

  val queue: SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])] =
    Source
      .queue[(HttpRequest, Promise[HttpResponse])](10000, OverflowStrategy.dropNew)
      .via(poolClientFlow)
      .toMat(Sink.foreach {
        case (Success(resp), p) => p.success(resp)
        case (Failure(e), p)    => p.failure(e)
      })(Keep.left)
      .run()

  def queueRequest(
      request: HttpRequest
  )(implicit ec: ExecutionContextExecutor): Future[HttpResponse] = {
    val responsePromise = Promise[HttpResponse]()
    queue.offer(request -> responsePromise).flatMap {
      case QueueOfferResult.Enqueued    => responsePromise.future
      case QueueOfferResult.Dropped     => Future.failed(new RuntimeException("Queue overflowed. Try again later."))
      case QueueOfferResult.Failure(ex) => Future.failed(ex)
      case QueueOfferResult.QueueClosed =>
        Future.failed(
          new RuntimeException("Queue was closed (pool shut down) while running the request. Try again later.")
        )
    }
  }
}
