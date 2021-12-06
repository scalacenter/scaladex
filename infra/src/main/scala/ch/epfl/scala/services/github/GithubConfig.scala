package ch.epfl.scala.services.github

import akka.actor.ActorSystem
import ch.epfl.scala.services.GithubService
import ch.epfl.scala.utils.Secret
import com.typesafe.config.Config

case class GithubConfig(token: Secret)

object GithubConfig {
  def from(config: Option[GithubConfig])(implicit actor: ActorSystem): GithubService =
    config match {
      case Some(config) =>
        println(s"a token has been provided ${config.token.decode}")
        new GithubClient(config)
      case None         => new NoOpGithubImpl()
    }

  def from(config: Config): Option[GithubConfig] = {
    val tokenOpt = if (config.hasPath("github.token")) Some(config.getString("github.token")) else None
    tokenOpt.map(Secret).map(GithubConfig(_))
  }
}
