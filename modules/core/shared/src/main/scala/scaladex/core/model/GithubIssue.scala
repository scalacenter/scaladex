package scaladex.core.model

case class GithubIssue(number: Int, title: String, url: Url) {
  override def toString: String = s"#$number - $title"
}
