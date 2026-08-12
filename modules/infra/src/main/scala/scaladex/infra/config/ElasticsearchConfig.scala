package scaladex.infra.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

final case class ElasticsearchConfig(
    port: Int,
    index: String,
    reset: Boolean,
    maxConnections: Int,
    connectTimeoutMs: Int,
    socketTimeoutMs: Int,
    connectionRequestTimeoutMs: Int
)

object ElasticsearchConfig:
  def load(): ElasticsearchConfig =
    from(ConfigFactory.load())

  def from(config: Config): ElasticsearchConfig =
    ElasticsearchConfig(
      config.getInt("scaladex.elasticsearch.port"),
      config.getString("scaladex.elasticsearch.index"),
      config.getBoolean("scaladex.elasticsearch.reset"),
      config.getInt("scaladex.elasticsearch.max-connections"),
      config.getInt("scaladex.elasticsearch.connect-timeout-ms"),
      config.getInt("scaladex.elasticsearch.socket-timeout-ms"),
      config.getInt("scaladex.elasticsearch.connection-request-timeout-ms")
    )
end ElasticsearchConfig
