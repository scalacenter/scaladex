package scaladex.core.service

import java.nio.file.Path

trait PomResolver {
  def resolve(groupId: String, artifactId: String, version: String): Option[Path]
}
