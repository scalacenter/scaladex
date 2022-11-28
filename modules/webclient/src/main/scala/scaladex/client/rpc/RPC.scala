package scaladex.client.rpc

import endpoints4s.xhr
import scaladex.core.api.SearchEndpoints

object RPC extends SearchEndpoints with xhr.future.Endpoints with xhr.JsonEntitiesFromSchemas {
  override def settings: xhr.EndpointsSettings = xhr.EndpointsSettings()
}
