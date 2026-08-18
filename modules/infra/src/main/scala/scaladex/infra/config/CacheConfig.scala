package scaladex.infra.config

import scala.concurrent.duration.*

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

final case class CacheConfig(refreshAfter: FiniteDuration, expireAfter: FiniteDuration, maxSize: Long)

object CacheConfig:
  def load(): CacheConfig =
    from(ConfigFactory.load())

  def from(config: Config): CacheConfig =
    def duration(key: String): FiniteDuration =
      FiniteDuration(config.getDuration(s"scaladex.caching.$key").toNanos, NANOSECONDS)
    CacheConfig(
      refreshAfter = duration("refresh-after"),
      expireAfter = duration("expire-after"),
      maxSize = config.getLong("scaladex.caching.max-size")
    )
end CacheConfig
