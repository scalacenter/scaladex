package scaladex.core.test

import scala.concurrent.Future

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
    val userState: UserState = UserState(Set.empty, organizations, info)
  }

  object Admin {
    val token = "admin"
    val info: UserInfo = UserInfo("admin", None, "admin-avatar-url", Secret(token))
    val organizations: Set[Project.Organization] = Set(Project.Organization("scalacenter"))
    val userState: UserState = UserState(Set.empty, organizations, info)
  }

  object Typelevel {
    val token = "typelevel"
    val info: UserInfo = UserInfo("typelevel-member", None, "typelevel-avatar-url", Secret(token))
    val projects: Set[Project.Reference] = Set(Values.Cats.reference)
    val organizations: Set[Project.Organization] = projects.map(_.organization)
    val userState: UserState = UserState(projects, organizations, info)
  }

  private val users: Map[Secret, UserState] = Map(
    Secret(Sonatype.token) -> Sonatype.userState,
    Secret(Admin.token) -> Admin.userState,
    Secret(Typelevel.token) -> Typelevel.userState
  )

  override def getToken(code: String): Future[Secret] =
    ???

  override def getUser(token: Secret): Future[UserInfo] =
    Future.successful(users(token).info)

  override def getUserState(token: Secret): Future[Option[UserState]] =
    Future.successful(users.get(token))
}
