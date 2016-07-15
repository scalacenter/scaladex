package ch.epfl.scala.index.model.release

/**
  * General Reference to Group MavenReference and Release.Reference
  * to a category form simpler usage.
  */
trait GeneralReference {

  def name: String
  def httpUrl: String
}
