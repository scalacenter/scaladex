package scaladex.server.config

import com.typesafe.config.Config

case class OAuth2Config(
    clientId: String,
    clientSecret: String,
    redirectUri: String
)

object OAuth2Config:
  def from(config: Config): OAuth2Config =
    val clientId = config.getString("scaladex.oauth2.client-id")
    val clientSecret = config.getString("scaladex.oauth2.client-secret")
    val redirectUri = config.getString("scaladex.oauth2.redirect-uri")
    OAuth2Config(clientId, clientSecret, redirectUri)
