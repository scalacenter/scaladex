package scaladex.client.rpc

import endpoints4s.fetch
import scaladex.core.api.SearchEndpoints

object RPC extends SearchEndpoints with fetch.future.Endpoints with fetch.JsonEntitiesFromSchemas {
  override def settings: fetch.EndpointsSettings = fetch.EndpointsSettings()
}
