package scaladex.server.config

import com.softwaremill.session.SessionConfig
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import scaladex.core.model.Env
import scaladex.infra.config.FilesystemConfig
import scaladex.infra.elasticsearch.ElasticsearchConfig
import scaladex.infra.github.GithubConfig
import scaladex.infra.storage.sql.DatabaseConfig

case class ServerConfig(
    env: Env,
    session: SessionConfig,
    endpoint: String,
    port: Int,
    oAuth2: OAuth2Config,
    database: DatabaseConfig,
    elasticsearch: ElasticsearchConfig,
    filesystem: FilesystemConfig,
    github: GithubConfig
)

object ServerConfig {
  def load(): ServerConfig = {
    val config: Config = ConfigFactory.load()

    val env = Env.from(config.getString("scaladex.env"))
    val session = SessionConfig.default(config.getString("scaladex.server.session-secret"))

    val endpoint = config.getString("scaladex.server.endpoint")
    val port = config.getInt("scaladex.server.port")
    val oauth2 = OAuth2Config.from(config)
    val database = DatabaseConfig.from(config).get
    val elasticsearch = ElasticsearchConfig.from(config)
    val tempDir = config.getString("scaladex.filesystem.temp")

    val filesystem = FilesystemConfig.from(config)
    val github = GithubConfig.from(config)

    ServerConfig(env, session, endpoint, port, oauth2, database, elasticsearch, filesystem, github)
  }
}
