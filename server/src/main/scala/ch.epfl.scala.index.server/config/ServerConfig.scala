package ch.epfl.scala.index.server.config

import ch.epfl.scala.services.storage.sql.DbConf
import com.softwaremill.session.SessionConfig
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

case class ServerConfig(
    tempDirPath: String,
    production: Boolean,
    oAuth2: OAuth2Config,
    session: SessionConfig,
    dbConf: DbConf
)

object ServerConfig {
  def load(): ServerConfig = {
    val config = ConfigFactory.load().getConfig("org.scala_lang.index.server")
    ServerConfig(
      tempDirPath = config.getString("tempDirPath"),
      production = config.getBoolean("production"),
      oAuth2(config.getConfig("oauth2")),
      SessionConfig.default(config.getString("sesssion-secret")),
      DbConf.from(config.getString("database-url")).get // can be refactored
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
