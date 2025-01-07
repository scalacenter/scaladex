package scaladex.infra.config

import scaladex.core.util.Secret

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

case class GithubConfig(token: Option[Secret])

object GithubConfig:
  def load(): GithubConfig =
    from(ConfigFactory.load())

  def from(config: Config): GithubConfig =
    val tokenOpt =
      if config.hasPath("scaladex.github.token") then Some(config.getString("scaladex.github.token"))
      else None
    GithubConfig(tokenOpt.map(Secret.apply))
