package ch.epfl.scala.index.model.misc

import ch.epfl.scala.index.newModel.Project
import ch.epfl.scala.utils.Secret

/**
 * github User info
 *
 * @param login the login name / Username
 * @param name the real name of the user
 * @param avatarUrl the avatar icon
 */
case class UserInfo(
    login: String,
    name: Option[String],
    avatarUrl: String,
    token: Secret
) extends AvatarUrl

case class UserState(
    repos: Set[Project.Reference],
    orgs: Set[Project.Organization],
    info: UserInfo
) {
  def isAdmin: Boolean = orgs.contains(Project.Organization("scalacenter"))
  def canEdit(githubRepo: Project.Reference): Boolean =
    isAdmin || repos.contains(githubRepo)
  def isSonatype: Boolean =
    orgs.contains(
      Project.Organization("sonatype")
    ) || info.login == "central-ossrh"
  def hasPublishingAuthority: Boolean = isAdmin || isSonatype
}
