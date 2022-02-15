package scaladex.infra.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

final case class ElasticsearchConfig(port: Int, index: String, reset: Boolean)

object ElasticsearchConfig {
  def load(): ElasticsearchConfig =
    from(ConfigFactory.load())

  def from(config: Config): ElasticsearchConfig =
    ElasticsearchConfig(
      config.getInt("scaladex.elasticsearch.port"),
      config.getString("scaladex.elasticsearch.index"),
      config.getBoolean("scaladex.elasticsearch.reset")
    )
}
