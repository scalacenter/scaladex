package scaladex.infra.config

import scala.concurrent.duration.*

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

final case class ElasticsearchConfig(
    port: Int,
    index: String,
    reset: Boolean,
    maxConnections: Int,
    connectTimeout: FiniteDuration,
    socketTimeout: FiniteDuration,
    connectionRequestTimeout: FiniteDuration
)

object ElasticsearchConfig:
  def load(): ElasticsearchConfig =
    from(ConfigFactory.load())

  def from(config: Config): ElasticsearchConfig =
    def duration(key: String): FiniteDuration =
      FiniteDuration(config.getDuration(s"scaladex.elasticsearch.$key").toNanos, NANOSECONDS)
    ElasticsearchConfig(
      config.getInt("scaladex.elasticsearch.port"),
      config.getString("scaladex.elasticsearch.index"),
      config.getBoolean("scaladex.elasticsearch.reset"),
      config.getInt("scaladex.elasticsearch.max-connections"),
      duration("connect-timeout"),
      duration("socket-timeout"),
      duration("connection-request-timeout")
    )
  end from
end ElasticsearchConfig
