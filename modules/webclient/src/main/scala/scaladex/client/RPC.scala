package scaladex.client

import scaladex.core.api.Endpoints

import endpoints4s.fetch

object RPC extends Endpoints with fetch.future.Endpoints with fetch.JsonEntitiesFromSchemas:
  override def settings: fetch.EndpointsSettings = fetch.EndpointsSettings()
