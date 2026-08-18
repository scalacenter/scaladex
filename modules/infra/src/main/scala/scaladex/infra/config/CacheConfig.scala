package scaladex.infra.config

import scala.concurrent.duration.*

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

final case class CacheConfig(ttl: FiniteDuration, maxSize: Long)

object CacheConfig:
  def load(): CacheConfig =
    from(ConfigFactory.load())

  def from(config: Config): CacheConfig =
    CacheConfig(
      FiniteDuration(config.getDuration("scaladex.caching.ttl").toNanos, NANOSECONDS),
      config.getLong("scaladex.caching.max-size")
    )
end CacheConfig
