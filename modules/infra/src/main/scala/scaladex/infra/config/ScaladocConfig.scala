package scaladex.infra.config

import com.typesafe.config.Config

case class ScaladocConfig(maxCacheBytes: Long, maxJarBytes: Long)

object ScaladocConfig:
  def from(config: Config): ScaladocConfig = ScaladocConfig(
    maxCacheBytes = config.getMemorySize("scaladex.scaladoc.max-cache-size").toBytes,
    maxJarBytes = config.getMemorySize("scaladex.scaladoc.max-jar-size").toBytes
  )
