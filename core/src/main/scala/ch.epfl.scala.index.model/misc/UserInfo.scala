package ch.epfl.scala.index.model.misc

import ch.epfl.scala.index.newModel.NewProject
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
    repos: Set[NewProject.Reference],
    orgs: Set[NewProject.Organization],
    info: UserInfo
) {
  def isAdmin: Boolean = orgs.contains(NewProject.Organization("scalacenter"))
  def canEdit(githubRepo: NewProject.Reference): Boolean =
    isAdmin || repos.contains(githubRepo)
  def isSonatype: Boolean =
    orgs.contains(
      NewProject.Organization("sonatype")
    ) || info.login == "central-ossrh"
  def hasPublishingAuthority: Boolean = isAdmin || isSonatype
}
