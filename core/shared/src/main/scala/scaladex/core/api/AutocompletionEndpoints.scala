package scaladex.core.api

import endpoints4s.algebra
import scaladex.core.model.Project
import scaladex.core.model.UserState
import scaladex.core.model.search.SearchParams

case class AutocompletionRequest(
    query: String,
    you: Boolean,
    topics: Seq[String],
    targetTypes: Seq[String],
    scalaVersions: Seq[String],
    scalaJsVersions: Seq[String],
    scalaNativeVersions: Seq[String],
    sbtVersions: Seq[String],
    contributingSearch: Boolean
) {
  def searchParams(user: Option[UserState]): SearchParams = {
    val userRepos = if (you) user.map(_.repos).getOrElse(Set.empty) else Set.empty[Project.Reference]
    SearchParams(
      queryString = query,
      page = 1,
      sorting = None,
      userRepos = userRepos,
      topics = topics,
      targetTypes = targetTypes,
      scalaVersions = scalaVersions,
      scalaJsVersions = scalaJsVersions,
      scalaNativeVersions = scalaNativeVersions,
      sbtVersions = sbtVersions,
      contributingSearch = contributingSearch
    )
  }
}

case class AutocompletionResponse(
    organization: String,
    repository: String,
    description: String
)

// Autocompletion endpoints are implemented by the server and invoked by the web client.
// There definition is shared here, for consistency.
trait AutocompletionEndpoints extends algebra.Endpoints with algebra.JsonEntitiesFromSchemas {

  /** Enrich a request with session information */
  def withOptionalSession[A](request: Request[A]): Request[WithSession[A]]

  /** A type `A` enriched with session information (unknown yet, defined by the web client and server) */
  type WithSession[A]

  // JSON schema of the autocompletion response entity
  implicit val autocompletionResponseSchema: JsonSchema[AutocompletionResponse] =
    field[String]("organization")
      .zip(field[String]("repository"))
      .zip(field[String]("description"))
      .xmap[AutocompletionResponse] {
        case (organization, repository, description) => AutocompletionResponse(organization, repository, description)
      } { autocompletionResponse =>
        (autocompletionResponse.organization, autocompletionResponse.repository, autocompletionResponse.description)
      }

  // Definition of the autocompletion query format
  val searchRequestQuery: QueryString[AutocompletionRequest] = (
    qs[String]("q") &
      qs[Option[String]]("you").xmap[Boolean](_.contains("✓"))(Option.when(_)("✓")) &
      qs[Seq[String]]("topics") &
      qs[Seq[String]]("targetTypes") &
      qs[Seq[String]]("scalaVersions") &
      qs[Seq[String]]("scalaJsVersions") &
      qs[Seq[String]]("scalaNativeVersions") &
      qs[Seq[String]]("sbtVersions") &
      qs[Option[Boolean]]("contributingSearch").xmap(_.getOrElse(false))(Option.when(_)(true))
  ).xmap((AutocompletionRequest.apply _).tupled)(Function.unlift(AutocompletionRequest.unapply))

  // Autocomplete endpoint definition
  val autocomplete: Endpoint[WithSession[AutocompletionRequest], Seq[AutocompletionResponse]] =
    endpoint(
      withOptionalSession(get(path / "api" / "autocomplete" /? searchRequestQuery)),
      ok(jsonResponse[Seq[AutocompletionResponse]])
    )

}
