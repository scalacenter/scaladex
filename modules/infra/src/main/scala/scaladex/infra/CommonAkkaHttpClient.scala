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

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings
import org.apache.pekko.pattern.CircuitBreaker
import org.apache.pekko.pattern.after
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.QueueOfferResult
import org.apache.pekko.stream.ThrottleMode
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.scaladsl.SourceQueueWithComplete
import org.slf4j.LoggerFactory

class CommonAkkaHttpClient(
    poolSettings: ConnectionPoolSettings,
    config: HttpClientConfig = HttpClientConfig.default,
    additionalRetry: HttpResponse => Boolean = _ => false
)(using system: ActorSystem)
    extends LazyLogging:

  private val accessLog = LoggerFactory.getLogger("scaladex.infra.http-client")

  private val maxConcurrentOffers = 256

  private val queue: SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])] =
    val requests =
      Source
        .queue[(HttpRequest, Promise[HttpResponse])](10000, OverflowStrategy.dropNew: @nowarn, maxConcurrentOffers)
    config.throttle
      .fold(requests) { t =>
        t.maxBurst match
          case Some(burst) => requests.throttle(t.requests, t.per, burst, ThrottleMode.Shaping)
          case None => requests.throttle(t.requests, t.per)
      }
      .via(Http().superPool[Promise[HttpResponse]](settings = poolSettings))
      .toMat(Sink.foreach {
        case (Success(resp), p) => p.success(resp)
        case (Failure(e), p) => p.failure(e)
      })(Keep.left)
      .run()
  end queue

  def queueRequest(request: HttpRequest)(using ExecutionContextExecutor): Future[HttpResponse] =
    breaker match
      case Some(cb) => cb.withCircuitBreaker(retryLoop(request, attempt = 0), isBreakerFailure)
      case None => retryLoop(request, attempt = 0)

  private def tryEnqueue(
      request: HttpRequest
  )(using ExecutionContextExecutor): Future[HttpResponse] =
    val responsePromise = Promise[HttpResponse]()
    val startNanos = System.nanoTime()
    val response = queue.offer(request -> responsePromise).flatMap {
      case QueueOfferResult.Enqueued => responsePromise.future
      case QueueOfferResult.Dropped => Future.failed(new RuntimeException("Queue overflowed. Try again later."))
      case QueueOfferResult.Failure(ex) => Future.failed(ex)
      case QueueOfferResult.QueueClosed =>
        Future.failed(
          new RuntimeException("Queue was closed (pool shut down) while running the request. Try again later.")
        )
    }
    if accessLog.isDebugEnabled then response.onComplete(logAccess(request, startNanos, _))
    response
  end tryEnqueue

  private def logAccess(request: HttpRequest, startNanos: Long, result: Try[HttpResponse]): Unit =
    val durationMs = (System.nanoTime() - startNanos) / 1000000
    val method = request.method.value
    val uri = request.uri
    result match
      case Success(response) => accessLog.debug(s"$method $uri ${response.status.intValue} ${durationMs}ms")
      case Failure(e) => accessLog.debug(s"$method $uri failed ${durationMs}ms (${e.getMessage})")

  private val breaker: Option[CircuitBreaker] =
    config.circuitBreaker.map(cb => CircuitBreaker(system.scheduler, cb.maxFailures, cb.callTimeout, cb.resetTimeout))

  private def isRetryable(response: HttpResponse): Boolean =
    response.status match
      case _: StatusCodes.ServerError => true
      case StatusCodes.TooManyRequests | StatusCodes.RequestTimeout => true
      case _ => additionalRetry(response)

  private def isBreakerFailure(result: Try[HttpResponse]): Boolean =
    result match
      case Success(response) => isRetryable(response)
      case Failure(_) => true

  private def retryLoop(request: HttpRequest, attempt: Int)(using ExecutionContextExecutor): Future[HttpResponse] =
    tryEnqueue(request).flatMap { response =>
      config.retry match
        case Some(retry) if isRetryable(response) && attempt < retry.maxRetries =>
          val backoff = retryDelay(response, retry, attempt)
          logger.warn(
            s"${response.status.intValue} for ${request.uri}, retrying in ${backoff.shortPrint} " +
              s"(attempt ${attempt + 1}/${retry.maxRetries})"
          )
          response.discardEntityBytes()
          after(backoff, system.scheduler)(retryLoop(request, attempt + 1))
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
