package scaladex.server.route.api

import scaladex.core.api.Endpoints

import endpoints4s.openapi
import endpoints4s.openapi.model.Info
import endpoints4s.openapi.model.OpenApi

/** Documentation of the public HTTP API of Scaladex
  */
object ApiDocumentation extends Endpoints with openapi.Endpoints with openapi.JsonEntitiesFromSchemas:
  val apiV0: OpenApi = openApi(Info(title = "Scaladex API", version = "v0"))(
    getProjects(v0),
    getProjectArtifacts(v0),
    getArtifactVersions(v0),
    getArtifact(v0)
  )

  val apiV1: OpenApi = openApi(Info(title = "Scaladex API", version = "v1"))(
    getProjects(v1),
    getProjectV1,
    getProjectVersionsV1,
    getLatestProjectVersionV1,
    getProjectVersionV1,
    getProjectArtifacts(v1),
    getArtifactVersions(v1),
    getLatestArtifactV1,
    getArtifact(v1)
  )
end ApiDocumentation
