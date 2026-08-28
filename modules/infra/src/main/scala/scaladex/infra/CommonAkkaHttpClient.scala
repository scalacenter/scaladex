package scaladex.infra

import scala.annotation.nowarn
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import scaladex.core.util.ScalaExtensions.*
import scaladex.infra.config.HttpClientConfig

import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCode
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.pattern.after
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.QueueOfferResult
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.scaladsl.SourceQueueWithComplete

abstract class CommonAkkaHttpClient(config: HttpClientConfig = HttpClientConfig.default)(using system: ActorSystem)
    extends FailFastCirceSupport
    with LazyLogging:

  def initPoolClientFlow: Flow[
    (HttpRequest, Promise[HttpResponse]),
    (Try[HttpResponse], Promise[HttpResponse]),
    Http.HostConnectionPool
  ]

  val queue: SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])] =
    val requests =
      Source
        .queue[(HttpRequest, Promise[HttpResponse])](10000, OverflowStrategy.dropNew: @nowarn)
        .via(initPoolClientFlow)
    config.throttle
      .fold(requests)(t => requests.throttle(t.requests, t.per))
      .toMat(Sink.foreach {
        case (Success(resp), p) => p.success(resp)
        case (Failure(e), p) => p.failure(e)
      })(Keep.left)
      .run()
  end queue

  def queueRequest(
      request: HttpRequest
  )(using ExecutionContextExecutor): Future[HttpResponse] =
    val responsePromise = Promise[HttpResponse]()
    queue.offer(request -> responsePromise).flatMap {
      case QueueOfferResult.Enqueued => responsePromise.future
      case QueueOfferResult.Dropped => Future.failed(new RuntimeException("Queue overflowed. Try again later."))
      case QueueOfferResult.Failure(ex) => Future.failed(ex)
      case QueueOfferResult.QueueClosed =>
        Future.failed(
          new RuntimeException("Queue was closed (pool shut down) while running the request. Try again later.")
        )
    }
  end queueRequest

  private val retriableStatusCodes: Set[StatusCode] = Set(
    StatusCodes.RequestTimeout,
    StatusCodes.TooManyRequests,
    StatusCodes.InternalServerError,
    StatusCodes.BadGateway,
    StatusCodes.ServiceUnavailable,
    StatusCodes.GatewayTimeout
  )

  def queueRequestWithRetry(
      request: HttpRequest,
      attempt: Int = 0
  )(using ExecutionContextExecutor): Future[HttpResponse] =
    queueRequest(request).flatMap { response =>
      config.retry match
        case Some(retry) if retriableStatusCodes(response.status) && attempt < retry.maxRetries =>
          val backoff = retryDelay(response, retry, attempt)
          logger.warn(
            s"${response.status.intValue} for ${request.uri}, retrying in ${backoff.shortPrint} " +
              s"(attempt ${attempt + 1}/${retry.maxRetries})"
          )
          response.discardEntityBytes()
          after(backoff, system.scheduler)(queueRequestWithRetry(request, attempt + 1))
        case _ =>
          Future.successful(response)
    }

  /** Honor the Retry-After header if present, otherwise use capped exponential backoff. */
  private def retryDelay(response: HttpResponse, retry: HttpClientConfig.Retry, attempt: Int): FiniteDuration =
    val retryAfter = response.headers.find(_.is("retry-after")).flatMap(h => h.value.toIntOption).map(_.seconds)
    val backoff = retry.initialDelay * math.pow(2, attempt).toLong
    val delay = retryAfter.getOrElse(backoff)
    if delay < retry.maxDelay then delay else retry.maxDelay
end CommonAkkaHttpClient
