package ch.epfl.scala.index.model.misc

/**
  * Github repository reference
  * @param organization organization name ex: akka
  * @param repo the repository name ex: akka-http, akka-stream
  */
case class GithubRepo(organization: String, repo: String) {

  /**
    * string representation of github repository
    * @return
    */
  override def toString = s"$organization/$repo"
}
