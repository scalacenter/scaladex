package scaladex.infra.config

import scala.concurrent.duration.*

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

final case class HttpClientConfig(
    throttle: Option[HttpClientConfig.Throttle],
    retry: Option[HttpClientConfig.Retry]
)

object HttpClientConfig:
  final case class Throttle(requests: Int, per: FiniteDuration)
  final case class Retry(maxRetries: Int, initialDelay: FiniteDuration, maxDelay: FiniteDuration)

  /** No throttling and no retry. */
  val default: HttpClientConfig =
    HttpClientConfig(throttle = None, retry = None)

  def load(path: String): HttpClientConfig =
    from(ConfigFactory.load().getConfig(path))

  def from(config: Config): HttpClientConfig =
    def duration(key: String): FiniteDuration =
      FiniteDuration(config.getDuration(key).toNanos, NANOSECONDS)
    val throttle =
      if config.hasPath("throttle") then Some(Throttle(config.getInt("throttle.requests"), duration("throttle.per")))
      else None
    val retry =
      if config.hasPath("retry") then
        Some(
          Retry(
            config.getInt("retry.max-retries"),
            duration("retry.initial-delay"),
            duration("retry.max-delay")
          )
        )
      else None
    HttpClientConfig(throttle, retry)
  end from
end HttpClientConfig
