package scaladex.client

import endpoints4s.fetch
import scaladex.core.api.Endpoints

object RPC extends Endpoints with fetch.future.Endpoints with fetch.JsonEntitiesFromSchemas:
  override def settings: fetch.EndpointsSettings = fetch.EndpointsSettings()
