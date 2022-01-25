package scaladex.core.test

import scala.concurrent.Future

import scaladex.core.model.Env
import scaladex.core.model.Project
import scaladex.core.model.UserInfo
import scaladex.core.model.UserState
import scaladex.core.service.GithubAuth
import scaladex.core.util.Secret

object MockGithubAuth extends GithubAuth {
  object Sonatype {
    val token = "sonatype"
    val info: UserInfo = UserInfo("central-ossrh", None, "sonatype-avatar-url", Secret(token))
    val organizations: Set[Project.Organization] = Set(Project.Organization("sonatype"))
    val userState: UserState = UserState(Set.empty, organizations, info, Env.Dev)
  }

  object Admin {
    val token = "admin"
    val info: UserInfo = UserInfo("admin", None, "admin-avatar-url", Secret(token))
    val organizations: Set[Project.Organization] = Set(Project.Organization("scalacenter"))
    val userState: UserState = UserState(Set.empty, organizations, info, Env.Dev)
  }

  object Typelevel {
    val token = "typelevel"
    val info: UserInfo = UserInfo("typelevel-member", None, "typelevel-avatar-url", Secret(token))
    val projects: Set[Project.Reference] = Set(Values.Cats.reference)
    val organizations: Set[Project.Organization] = projects.map(_.organization)
    val userState: UserState = UserState(projects, organizations, info, Env.Dev)
  }

  private val users: Map[String, UserState] = Map(
    Sonatype.token -> Sonatype.userState,
    Admin.token -> Admin.userState,
    Typelevel.token -> Typelevel.userState
  )

  override def getUserStateWithToken(token: String): Future[UserState] =
    Future.successful(users(token))

  override def getUserStateWithOauth2(
      clientId: String,
      clientSecret: String,
      code: String,
      redirectUri: String
  ): Future[UserState] =
    ???
}
