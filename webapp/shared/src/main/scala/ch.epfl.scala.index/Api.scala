package ch.epfl.scala.index

import scala.concurrent.Future

case class UserInfo(login: String, name: String, avatarUrl: String) {
  def sizedAvatarUrl(size: Int) = avatarUrl + "&s" + size.toString
}

trait Api {
  def find(q: String): Future[(Long, List[Project])]
  def userInfo(): Option[UserInfo]
  def projectPage(groupId: String, artifactId: String): Future[Option[(Project, Option[String])]]
}