package scaladex.infra

import scala.concurrent.Promise
import scala.util.Try

import scaladex.infra.config.HttpClientConfig

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings
import org.apache.pekko.stream.scaladsl.Flow

/** The one throttled request queue for `repo1.maven.org`, shared by every Maven Central client (index-chunk
  * scanning, per-group directory listing, pom fetching). Each `CommonAkkaHttpClient` subclass otherwise builds
  * its own independent throttle, so two clients hitting the same host each configured at, say, 1 req/s combine
  * to ~2 req/s in practice. Constructing a single `MavenCentralHttpQueue` and injecting it into both clients
  * keeps the configured rate an actual ceiling on combined Maven Central traffic, however many callers are
  * active — including the recent DiscoveryService backfill (chunk scanning and group syncing running together).
  */
class MavenCentralHttpQueue(config: HttpClientConfig = HttpClientConfig.default)(using system: ActorSystem)
    extends CommonAkkaHttpClient(config):
  def initPoolClientFlow: Flow[
    (HttpRequest, Promise[HttpResponse]),
    (Try[HttpResponse], Promise[HttpResponse]),
    Http.HostConnectionPool
  ] = Http()
      .cachedHostConnectionPoolHttps[Promise[HttpResponse]](
        "repo1.maven.org",
        settings = ConnectionPoolSettings("max-open-requests = 32")
      )
end MavenCentralHttpQueue
