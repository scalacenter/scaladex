package scaladex.infra.config

import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

final case class DatabasePoolConfig(
    maximumPoolSize: Int,
    minimumIdle: Int,
    connectionTimeoutMs: Long,
    idleTimeoutMs: Long,
    maxLifetimeMs: Long,
    leakDetectionThresholdMs: Long
)

object DatabasePoolConfig:
  val default: DatabasePoolConfig = DatabasePoolConfig(
    maximumPoolSize = 20,
    minimumIdle = 5,
    connectionTimeoutMs = 30000,
    idleTimeoutMs = 600000,
    maxLifetimeMs = 1800000,
    leakDetectionThresholdMs = 60000
  )

  def load(): Try[DatabasePoolConfig] =
    val config = ConfigFactory.load()
    from(config)

  def from(config: Config): Try[DatabasePoolConfig] = Try {
    val poolConfig = config.getConfig("scaladex.database.pool")
    DatabasePoolConfig(
      maximumPoolSize = poolConfig.getInt("maximum-pool-size"),
      minimumIdle = poolConfig.getInt("minimum-idle"),
      connectionTimeoutMs = poolConfig.getLong("connection-timeout-ms"),
      idleTimeoutMs = poolConfig.getLong("idle-timeout-ms"),
      maxLifetimeMs = poolConfig.getLong("max-lifetime-ms"),
      leakDetectionThresholdMs = poolConfig.getLong("leak-detection-threshold-ms")
    )
  }
end DatabasePoolConfig
