package scaladex.core.api

import endpoints4s.Invalid
import endpoints4s.algebra

case class ScaladocLocationRequest(
  organization: String,
  artifact: String,
  version: Option[String]
)

case class ScaladocLocationResponse(uri: String)

/**
 * Endpoints to query information about Scala projects.
 */
trait ProjectsEndpoints extends algebra.Endpoints with algebra.JsonEntitiesFromSchemas {

  implicit val scaladocLocationResponseSchema: JsonSchema[ScaladocLocationResponse] =
    field[String]("uri", Some("URI of the Scaladoc documentation"))
      .xmap(ScaladocLocationResponse)(_.uri)

  val scaladocLocationQuery: QueryString[ScaladocLocationRequest] = {
    val organizationQuery =
      qs[String]("organization", docs = Some("Organization / groupID of the release (e.g., 'org.typelevel')"))
    val artifactQuery =
      qs[String]("artifact", docs = Some("Artifact of the release (e.g., 'cats-core')"))
    val optionalVersionQuery =
      qs[Option[String]]("version", docs = Some("Release version"))
    (organizationQuery & artifactQuery & optionalVersionQuery)
      .xmap((ScaladocLocationRequest.apply _).tupled)(Function.unlift(ScaladocLocationRequest.unapply))
  }

  val scaladocLocation: Endpoint[ScaladocLocationRequest, Either[Invalid, ScaladocLocationResponse]] =
    endpoint(
      get(path / "api" / "project / scaladoc" /? scaladocLocationQuery),
      response(NotFound, clientErrorsResponseEntity, docs = Some("The artifact could not be found"))
        .orElse(ok(jsonResponse[ScaladocLocationResponse])),
      EndpointDocs()
        .withDescription(Some("Get the URI of the Scaladoc documentation of a release"))
    )

}
