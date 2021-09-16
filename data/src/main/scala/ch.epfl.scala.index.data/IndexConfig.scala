package ch.epfl.scala.index.data

import ch.epfl.scala.index.model.DataPaths
import ch.epfl.scala.index.model.Env
import ch.epfl.scala.services.storage.sql.DbConf
import com.typesafe.config.ConfigFactory

case class IndexConfig(env: Env, db: DbConf, dataPaths: DataPaths)

object IndexConfig {

  def load(): IndexConfig = {
    val conf = ConfigFactory.load()
    val dbConf = conf.getConfig("database")
    val dataPathConf = conf.getConfig("data-paths")
    val env = Env.from(conf.getConfig("app").getString("env"))
    val contrib = dataPathConf.getString("contrib")
    val index = dataPathConf.getString("index")
    val credentials = dataPathConf.getString("credentials")
    IndexConfig(
      env = env,
      db = DbConf.from(dbConf.getString("database-url")).get,
      dataPaths = DataPaths.from(contrib, index, credentials, env)
    )
  }

}
