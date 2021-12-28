package scaladex.data

import com.typesafe.config.ConfigFactory
import scaladex.core.model.Env
import scaladex.infra.storage.DataPaths
import scaladex.infra.storage.sql.DatabaseConfig

case class IndexConfig(env: Env, db: DatabaseConfig, dataPaths: DataPaths)

object IndexConfig {

  def load(): IndexConfig = {
    val conf = ConfigFactory.load()
    val dataPathConf = conf.getConfig("data-paths")
    val env = Env.from(conf.getConfig("app").getString("env"))
    val contrib = dataPathConf.getString("contrib")
    val index = dataPathConf.getString("index")
    val credentials = dataPathConf.getString("credentials")
    IndexConfig(
      env = env,
      db = DatabaseConfig.from(conf).get,
      dataPaths = DataPaths.from(contrib, index, credentials, env)
    )
  }

}
