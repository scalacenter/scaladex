package ch.epfl.scala.index.model.misc

case class GithubIssue(number: Int, title: String, url: Url) {
  override def toString = s"#$number - $title"
}
