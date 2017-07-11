package ch.epfl.scala.index.model.misc

/**
 * @param organization (ex: akka)
 * @param repository (ex: akka-http, akka-stream)
 */
case class GithubRepo(organization: String, repository: String)
    extends Ordered[GithubRepo] {

  override def toString = s"$organization/$repository"

  override def compare(that: GithubRepo): Int =
    GithubRepo.ordering.compare(this, that)
}

object GithubRepo {
  implicit val ordering: Ordering[GithubRepo] = Ordering.by { repo =>
    (
      repo.organization.toLowerCase,
      repo.repository.toLowerCase
    )
  }
}
