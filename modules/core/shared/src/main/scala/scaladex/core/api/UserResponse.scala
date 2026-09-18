package scaladex.core.api

import scaladex.core.model.Project

/** Information about the user authenticated by the token passed in the request. */
case class UserResponse(
    login: String,
    name: Option[String],
    avatarUrl: String,
    isAdmin: Boolean,
    organizations: Seq[Project.Organization],
    repositories: Seq[Project.Reference]
)
