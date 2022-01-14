package scaladex.core.service

import java.nio.file.Path

import scala.concurrent.Future

trait PomResolver {
  def resolve(groupId: String, artifactId: String, version: String): Future[Option[Path]]
}
