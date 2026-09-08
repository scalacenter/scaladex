package scaladex.infra.config

import com.typesafe.config.Config

case class ScaladocConfig(maxCacheBytes: Long, maxUnpackedBytes: Long)

object ScaladocConfig:
  def from(config: Config): ScaladocConfig = ScaladocConfig(
    maxCacheBytes = config.getMemorySize("scaladex.scaladoc.max-cache-size").toBytes,
    maxUnpackedBytes = config.getMemorySize("scaladex.scaladoc.max-unpacked-size").toBytes
  )
