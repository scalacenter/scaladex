package scaladex.data

import com.typesafe.config.ConfigFactory
import scaladex.core.model.Env
import scaladex.infra.config.FilesystemConfig
import scaladex.infra.storage.sql.DatabaseConfig

case class IndexConfig(env: Env, database: DatabaseConfig, filesystem: FilesystemConfig)

object IndexConfig {

  def load(): IndexConfig = {
    val config = ConfigFactory.load()
    val env = Env.from(config.getString("scaladex.env"))
    val contrib = config.getString("scaladex.filesystem.contrib")
    val index = config.getString("scaladex.filesystem.index")
    val credentials = config.getString("scaladex.filesystem.credentials")
    IndexConfig(
      env = env,
      database = DatabaseConfig.from(config).get,
      filesystem = FilesystemConfig.from(config)
    )
  }

}
