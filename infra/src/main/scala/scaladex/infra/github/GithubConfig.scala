package scaladex.infra.github

import com.typesafe.config.Config
import scaladex.core.util.Secret

case class GithubConfig(token: Secret)

object GithubConfig {
  def from(config: Config): Option[GithubConfig] = {
    val tokenOpt = if (config.hasPath("github.token")) Some(config.getString("github.token")) else None
    tokenOpt.map(Secret).map(GithubConfig(_))
  }
}
