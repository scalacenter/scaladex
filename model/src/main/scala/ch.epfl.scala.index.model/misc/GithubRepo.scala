package ch.epfl.scala.index.model.misc

/**
  * @param organization (ex: akka)
  * @param repository (ex: akka-http, akka-stream)
  */
case class GithubRepo(organization: String, repository: String) {
  override def toString = s"$organization/$repository"
}
