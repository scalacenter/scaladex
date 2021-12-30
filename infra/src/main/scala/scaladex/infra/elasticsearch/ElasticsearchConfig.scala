package scaladex.infra.elasticsearch

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

final case class ElasticsearchConfig(port: Int, index: String)

object ElasticsearchConfig {
  def load(): ElasticsearchConfig =
    from(ConfigFactory.load())

  def from(config: Config): ElasticsearchConfig =
    ElasticsearchConfig(
      config.getInt("scaladex.elasticsearch.port"),
      config.getString("scaladex.elasticsearch.index")
    )
}
