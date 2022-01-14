package scaladex.infra.config

import java.nio.file.Path
import java.nio.file.Paths

import com.typesafe.config.Config

case class FilesystemConfig(temp: Path, index: Path, contrib: Path, credentials: Path)

object FilesystemConfig {
  def from(config: Config): FilesystemConfig = {
    val temp = Paths.get(config.getString("scaladex.filesystem.temp"))
    val index = Paths.get(config.getString("scaladex.filesystem.index"))
    val contrib = Paths.get(config.getString("scaladex.filesystem.contrib"))
    val credentials = Paths.get(config.getString("scaladex.filesystem.credentials"))
    FilesystemConfig(temp, index, contrib, credentials)
  }
}
