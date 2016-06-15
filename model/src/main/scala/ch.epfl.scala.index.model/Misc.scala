package ch.epfl.scala.index.model

case class Url(target: String)

case class GithubRepo(organization: String, repo: String) {
  override def toString = s"$organization/$repo"
}

case class GithubInfo(
  // html formatted
  readme: Option[String] = None,

  description: Option[String] = None,

  // http://typelevel.org/cats/
  homepage: Option[Url] = None,

  logo: Option[Url] = None,

  stars: Option[Int] = None,

  forks: Option[Int] = None
)

case class UserInfo(login: String, name: String, avatarUrl: String) {
  def sizedAvatarUrl(size: Int) = avatarUrl + "&s=" + size.toString
}

case class Pagination(current: PageIndex, totalPages: Int, total: Long)