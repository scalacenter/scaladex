package ch.epfl.scala.services.github

import ch.epfl.scala.utils.Secret
import com.typesafe.config.Config

case class GithubConfig(token: Secret)

object GithubConfig {
  def from(config: Config): Option[GithubConfig] = {
    val tokenOpt = if (config.hasPath("github.token")) Some(config.getString("github.token")) else None
    tokenOpt.map(Secret).map(GithubConfig(_))
  }
}
