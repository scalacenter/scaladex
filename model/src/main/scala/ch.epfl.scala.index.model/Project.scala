package ch.epfl.scala.index.model

// typelevel/cats
case class Project(
  reference: Project.Reference,

  artifacts: List[Artifact],

  github: Option[GithubInfo] = None,

  // predefined keywords (ex: database)
  keywords: List[String] = Nil,
  
  // http://stackoverflow.com/tags
  stackOverflowTags: List[String] = Nil,
  
  // @ handle (ex: @akkateam, @typelevel)
  twitter: Option[String] = None,
    
  // agglomerate of github organizations: lightbend(akka, play, ...), verizon(verizon, oncue), etc
  parentOrganization: Option[String] = None,

  // absolute url to a logo (ex: http://spark.apache.org/images/spark-logo-trademark.png)
  logoImageUrl: Option[Url] = None,

  _id: Option[Int] = None
) {
  def githubRepo = GithubRepo(reference.organization, reference.repository)
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

case class GithubInfo(
  // html formatted
  readme: Option[String] = None,

  description: Option[String] = None,

  // http://typelevel.org/cats/
  homepage: Option[Url] = None,

  logo: Option[Url] = None,

  stars: Option[Int] = None,

  forks: Option[Int] = None
)