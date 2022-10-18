package scaladex.core.api

import endpoints4s.algebra

// Autocompletion endpoints are implemented by the server and invoked by the web client.
// There definition is shared here, for consistency.
trait SearchEndpoints extends algebra.Endpoints with algebra.JsonEntitiesFromSchemas {

  implicit val autocompletionResponse: JsonSchema[AutocompletionResponse] =
    field[String]("organization")
      .zip(field[String]("repository"))
      .zip(field[String]("description"))
      .xmap[AutocompletionResponse] {
        case (organization, repository, description) => AutocompletionResponse(organization, repository, description)
      } { autocompletionResponse =>
        (autocompletionResponse.organization, autocompletionResponse.repository, autocompletionResponse.description)
      }

  // Definition of the autocompletion query format
  val autocompletionParams: QueryString[AutocompletionParams] = (
    qs[String]("q", docs = Some("Main query (e.g., 'json', 'testing', etc.)")) &
      qs[Option[String]]("you", docs = Some("Used internally by Scaladex web user interface"))
        .xmap[Boolean](_.contains("✓"))(Option.when(_)("✓")) &
      qs[Seq[String]]("topics", docs = Some("Filter the results matching the given topics only")) &
      qs[Seq[String]](
        "languages",
        docs = Some(
          "Filter the results matching the given language versions only (e.g., '3', '2.13', '2.12', '2.11', 'java')"
        )
      ) &
      qs[Seq[String]](
        "platforms",
        docs = Some("Filter the results matching the given platforms only (e.g., 'jvm', 'sjs1', 'native0.4', 'sbt1.0')")
      ) &
      qs[Option[Boolean]]("contributingSearch").xmap(_.getOrElse(false))(Option.when(_)(true))
  ).xmap((AutocompletionParams.apply _).tupled)(Function.unlift(AutocompletionParams.unapply))

  // Autocomplete endpoint definition
  val autocomplete: Endpoint[AutocompletionParams, Seq[AutocompletionResponse]] =
    endpoint(
      get(path / "api" / "autocomplete" /? autocompletionParams),
      ok(jsonResponse[Seq[AutocompletionResponse]])
    )

}
