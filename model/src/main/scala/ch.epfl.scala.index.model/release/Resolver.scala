package ch.epfl.scala.index.model
package release

/**
 * A resolver is a place to download artifact (ex: Maven Central, etc)
 * @param sbt see http://www.scala-sbt.org/0.13/docs/Resolvers.html#Predefined
 */
trait Resolver {
  def name: String
  def url: Option[String]
  def sbt: Option[String]
}

case object JCenter extends Resolver {
  def name = "Bintray's JCenter"
  def url = Some("https://jcenter.bintray.com")
  def sbt = Some("Resolver.bintrayJCenter")
}

case class BintrayResolver(owner: String, repo: String) extends Resolver {
  def name = s"Bintray $owner $repo"
  def url = Some(s"https://dl.bintray.com/$owner/$repo")
  def sbt = Some(s"""Resolver.bintrayRepo("$owner", "$repo")""")
}

case object UserPublished extends Resolver {
  def name = "User Published"
  def url = None
  def sbt = None
}
