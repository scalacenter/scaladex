package ch.epfl.scala.index.model
package release

trait Resolver{
  def name: String
  def url: String
  def sbt: String
}

case class BintrayResolver(owner: String, repo: String) extends Resolver {
  def name = s"Bintray $owner $repo"
  def url = s"https://dl.bintray.com/$owner/$repo"
  def sbt = s"""Resolver.bintrayRepo("$owner", "$repo")"""
}
