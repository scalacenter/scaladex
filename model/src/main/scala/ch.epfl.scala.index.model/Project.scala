package ch.epfl.scala.index.model

// typelevel/cats
case class Project(
  reference: Project.Reference,
  artifacts: List[Artifact],
  // predefined keywords (ex: database)
  keywords: List[String] = Nil,
  // http://stackoverflow.com/tags
  stackOverflowTags: List[String] = Nil,
  // @ handle (ex: @akkateam, @typelevel)
  twitter: Option[String] = None,
  // github repo associated with a gitter.im chat
  gitterIm: Boolean = false,
  // agglomerate of github organizations: lightbend(akka, play, ...), verizon(verizon, oncue), etc
  parentOrganization: Option[String] = None,

  // absolute url to a logo (ex: http://typelevel.org/img/logo.svg)
  logoImageUrl: Option[Url] = None
) {
  def github = GithubRepo(reference.organization, reference.repository)
}

object Project{
  case class Reference(
    // github organization. ex: typelevel, akka, etc
    organization: String,

    // github repository. ex: cats, akka, etc
    repository: String
  )
}

case class Url(target: String)

case class GithubRepo(organization: String, repo: String) {
  override def toString = s"$organization/$repo"
}