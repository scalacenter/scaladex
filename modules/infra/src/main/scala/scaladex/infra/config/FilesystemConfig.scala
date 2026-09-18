package scaladex.infra.config

import java.nio.file.Path
import java.nio.file.Paths

import com.typesafe.config.Config

case class FilesystemConfig(temp: Path, index: Path, contrib: Path, scaladoc: Path)

object FilesystemConfig:
  def from(config: Config): FilesystemConfig =
    val temp = Paths.get(config.getString("scaladex.filesystem.temp"))
    val index = Paths.get(config.getString("scaladex.filesystem.index"))
    val contrib = Paths.get(config.getString("scaladex.filesystem.contrib"))
    val scaladoc = Paths.get(config.getString("scaladex.filesystem.scaladoc"))
    FilesystemConfig(temp, index, contrib, scaladoc)
