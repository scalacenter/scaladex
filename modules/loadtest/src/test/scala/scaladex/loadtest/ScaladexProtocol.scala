package scaladex.loadtest

import io.gatling.core.Predef.*
import io.gatling.http.Predef.*
import io.gatling.http.protocol.HttpProtocolBuilder

object ScaladexProtocol:

  val baseUrl: String = sys.props.getOrElse("loadtest.baseUrl", "http://localhost:8080")

  val httpProtocol: HttpProtocolBuilder = http
    .baseUrl(baseUrl)
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.9")
    .userAgentHeader("ScaladexLoadTest/1.0 (Gatling)")
    .shareConnections
end ScaladexProtocol
