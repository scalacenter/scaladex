package scaladex.core.model

/**
 * A resolver is a place to download artifact (ex: Maven Central, etc)
 * @param sbt see http://www.scala-sbt.org/0.13/docs/Resolvers.html#Predefined
 */
sealed trait Resolver {
  def name: String
  def url: Option[String]
  def sbt: Option[String]
}

case object JCenter extends Resolver {
  def name = "Bintray's JCenter"
  def url: Some[String] = Some("https://jcenter.bintray.com")
  def sbt: Some[String] = Some("Resolver.bintrayJCenter")
}

case class BintrayResolver(owner: String, repo: String) extends Resolver {
  def name: String = s"Bintray $owner $repo"
  def url: Some[String] = Some(s"https://dl.bintray.com/$owner/$repo")
  def sbt: Some[String] = Some(s"""Resolver.bintrayRepo("$owner", "$repo")""")
}

case object UserPublished extends Resolver {
  def name = "User Published"
  def url = None
  def sbt = None
}
object Resolver {
  def from(name: String): Option[Resolver] = name match {
    case "Bintray's JCenter"     => Some(JCenter)
    case s"Bintray $owner $repo" => Some(BintrayResolver(owner, repo))
    case "User Published"        => Some(UserPublished)
    case _                       => None
  }
}
