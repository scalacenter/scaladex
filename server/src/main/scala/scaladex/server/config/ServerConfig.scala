package scaladex.server.config

import com.softwaremill.session.SessionConfig
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import scaladex.core.model.Env
import scaladex.infra.github.GithubConfig
import scaladex.infra.storage.DataPaths
import scaladex.infra.storage.sql.DatabaseConfig

case class ServerConfig(
    tempDirPath: String,
    production: Boolean,
    oAuth2: OAuth2Config,
    session: SessionConfig,
    api: ApiConfig,
    dbConf: DatabaseConfig,
    dataPaths: DataPaths,
    github: Option[GithubConfig]
)
case class ApiConfig(endpoint: String, port: Int, env: Env)

object ServerConfig {
  def load(): ServerConfig = {
    val allConfig: Config = ConfigFactory.load()
    val config = allConfig.getConfig("server")
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
      DatabaseConfig.from(allConfig).get, // can be refactored
      dataPaths = DataPaths.from(contrib, index, credentials, env),
      github = GithubConfig.from(allConfig)
    )
  }

  private def oAuth2(config: Config): OAuth2Config =
    OAuth2Config(
      clientId = config.getString("client-id"),
      clientSecret = config.getString("client-secret"),
      redirectUri = config.getString("uri") + "/callback/done"
    )
}
