package scaladex.server

import org.apache.pekko.http.scaladsl.server.Directive0
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.RouteResult
import org.slf4j.LoggerFactory

/** One line per HTTP request, emitted at DEBUG to the dedicated `scaladex.server.http-access` logger (set that logger
  * to DEBUG to enable it).
  */
object HttpRequestLogging:
  private val log = LoggerFactory.getLogger("scaladex.server.http-access")

  val logAccess: Directive0 =
    (extractClientIP & extractRequest).tflatMap {
      case (clientIp, request) =>
        if !log.isDebugEnabled then pass
        else
          val startNanos = System.nanoTime()
          val remoteAddress = clientIp.toOption.map(_.getHostAddress).getOrElse("-")
          val method = request.method.value
          val path = request.uri.path.toString + request.uri.rawQueryString.map("?" + _).getOrElse("")
          mapRouteResult { result =>
            val durationMs = (System.nanoTime() - startNanos) / 1000000
            result match
              case RouteResult.Complete(response) =>
                log.debug(s"$remoteAddress $method $path ${response.status.intValue} ${durationMs}ms")
              case RouteResult.Rejected(_) =>
                log.debug(s"$remoteAddress $method $path rejected ${durationMs}ms")
            result
          }
    }
end HttpRequestLogging
