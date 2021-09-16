package ch.epfl.scala.index.server.config

import ch.epfl.scala.index.model.Env
import ch.epfl.scala.services.storage.DataPaths
import ch.epfl.scala.services.storage.sql.DbConf
import com.softwaremill.session.SessionConfig
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

case class ServerConfig(
    tempDirPath: String,
    production: Boolean,
    oAuth2: OAuth2Config,
    session: SessionConfig,
    api: ApiConfig,
    dbConf: DbConf,
    dataPaths: DataPaths
)
case class ApiConfig(endpoint: String, port: Int, env: Env)

object ServerConfig {
  def load(): ServerConfig = {
    val allConfig = ConfigFactory.load()
    val config = allConfig.getConfig("org.scala_lang.index.server")
    val dbConfig = allConfig.getConfig("database")
    val apiConfig = allConfig.getConfig("app")
    val dataPathConf = allConfig.getConfig("data-paths")
    val contrib = dataPathConf.getString("contrib")
    val index = dataPathConf.getString("index")
    val credentials = dataPathConf.getString("credentials")
    val env = Env.from(apiConfig.getString("env"))
    ServerConfig(
      tempDirPath = config.getString("tempDirPath"),
      production = config.getBoolean("production"),
      oAuth2(config.getConfig("oauth2")),
      SessionConfig.default(config.getString("sesssion-secret")),
      ApiConfig(apiConfig.getString("endpoint"), apiConfig.getInt("port"), env),
      DbConf.from(dbConfig.getString("database-url")).get, // can be refactored
      dataPaths = DataPaths.from(contrib, index, credentials, env)
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
