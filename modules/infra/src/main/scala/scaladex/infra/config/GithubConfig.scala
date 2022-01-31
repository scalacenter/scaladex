package scaladex.infra.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import scaladex.core.util.Secret

case class GithubConfig(token: Option[Secret])

object GithubConfig {
  def load(): GithubConfig =
    from(ConfigFactory.load())

  def from(config: Config): GithubConfig = {
    val tokenOpt =
      if (config.hasPath("scaladex.github.token")) Some(config.getString("scaladex.github.token"))
      else None
    GithubConfig(tokenOpt.map(Secret.apply))
  }
}
