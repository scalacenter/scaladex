package ch.epfl.scala.index

import model._
import scala.concurrent.Future

package api {
  case class UserInfo(login: String, name: String, avatarUrl: String) {
    def sizedAvatarUrl(size: Int) = avatarUrl + "&s" + size.toString
  }

  trait Api {
    def userInfo(): Option[UserInfo]
    def find(q: String, page: PageIndex): Future[(Pagination, List[Project])]
    def projectPage(artifact: Artifact.Reference): Future[Option[Project]]
  }
}
