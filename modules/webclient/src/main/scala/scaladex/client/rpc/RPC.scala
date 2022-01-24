package scaladex.client.rpc

import endpoints4s.xhr
import scaladex.core.api.AutocompletionEndpoints

object RPC extends AutocompletionEndpoints with xhr.future.Endpoints with xhr.JsonEntitiesFromSchemas {
  // On the client-side, the browser always sends the session under the hood,
  // so there is nothing more to do.
  type WithSession[A] = A
  def withOptionalSession[A](request: Request[A]): Request[WithSession[A]] = request
}
