package scaladex.infra.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

case class MavenCentralConfig(httpClient: HttpClientConfig)

object MavenCentralConfig:
  def load(): MavenCentralConfig =
    from(ConfigFactory.load())

  def from(config: Config): MavenCentralConfig =
    MavenCentralConfig(HttpClientConfig.from(config.getConfig("scaladex.maven-central.http-client")))
