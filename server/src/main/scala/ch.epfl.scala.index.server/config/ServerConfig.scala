package ch.epfl.scala.index.server.config

import com.softwaremill.session.SessionConfig
import com.typesafe.config.{Config, ConfigFactory}

case class ServerConfig(tempDirPath: String, production: Boolean, oAuth2: OAuth2Config, session: SessionConfig)

object ServerConfig {
  def load(): ServerConfig = {
    val config = ConfigFactory.load().getConfig("org.scala_lang.index.server")
    ServerConfig(
      tempDirPath = config.getString("tempDirPath"),
      production = config.getBoolean("production"),
      oAuth2(config.getConfig("oauth2")),
      SessionConfig.default(config.getString("sesssion-secret"))
    )
  }

  private def oAuth2(config: Config): OAuth2Config = {
    OAuth2Config(
      clientId = config.getString("client-id"),
      clientSecret = config.getString("client-secret"),
      redirectUri = config.getString("uri") + "/callback/done"
    )
  }
}