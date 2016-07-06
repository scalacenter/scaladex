package ch.epfl.scala.index.model
package release

trait Resolver{
  def name: String
  def url: String
}

case class Bintray(owner: String, repo: String) extends Resolver {
  def name = s"Bintray $onwer $repo"
  def url = s"https://dl.bintray.com/$owner/$repo"
}