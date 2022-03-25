package scaladex.client.rpc

import endpoints4s.xhr
import scaladex.core.api.AutocompletionParams
import scaladex.core.api.SearchEndpoints

object RPC extends SearchEndpoints with xhr.future.Endpoints with xhr.JsonEntitiesFromSchemas {
  // On the client-side, the browser always sends the session under the hood,
  // so there is nothing more to do.
  type WithSession = AutocompletionParams
  def withOptionalSession(request: Request[AutocompletionParams]): Request[AutocompletionParams] = request
}
