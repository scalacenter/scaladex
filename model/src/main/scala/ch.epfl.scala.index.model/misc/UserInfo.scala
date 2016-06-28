package ch.epfl.scala.index.model.misc

/**
 * github User info
 * @param login the login name / Username
 * @param name the real name of the user
 * @param avatarUrl the avatar icon
 */
case class UserInfo(login: String, name: String, avatarUrl: String) extends AvatarUrl
