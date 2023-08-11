package scaladex.core.model

import scaladex.core.util.Secret

/**
 * github User info
 *
 * @param login the login name / Username
 * @param name the real name of the user
 * @param avatarUrl the avatar icon
 */
case class UserInfo(login: String, name: Option[String], avatarUrl: String, token: Secret) extends AvatarUrl

case class UserState(repos: Set[Project.Reference], orgs: Set[Project.Organization], info: UserInfo) {
  def isAdmin(env: Env): Boolean = orgs.contains(Project.Organization("scalacenter")) || env.isLocal
  def canEdit(githubRepo: Project.Reference, env: Env): Boolean =
    isAdmin(env) || repos.contains(githubRepo)
  def isSonatype: Boolean =
    orgs.contains(
      Project.Organization("sonatype")
    ) || info.login == "central-ossrh"
  def hasPublishingAuthority(env: Env): Boolean = isAdmin(env) || isSonatype
}
