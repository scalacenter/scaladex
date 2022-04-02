package scaladex.server

import java.time.Instant

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.PathMatcher1
import akka.http.scaladsl.unmarshalling.Unmarshaller
import scaladex.core.model.Artifact
import scaladex.core.model.Project
import scaladex.core.model.SemanticVersion
import scaladex.core.model.search.PageParams

package object route {

  val organizationM: PathMatcher1[Project.Organization] = Segment.map(Project.Organization.apply)
  val repositoryM: PathMatcher1[Project.Repository] = Segment.map(Project.Repository.apply)
  val projectM: PathMatcher1[Project.Reference] = (organizationM / repositoryM).tmap {
    case (orga, repo) => Tuple1(Project.Reference(orga, repo))
  }

  val artifactM: PathMatcher1[Artifact.Name] = Segment.map(Artifact.Name.apply)
  val versionM: PathMatcher1[SemanticVersion] = Segment.flatMap(SemanticVersion.parse)

  val mavenReferenceM: PathMatcher[Tuple1[Artifact.MavenReference]] = (Segment / Segment / Segment).tmap {
    case (groupId, artifactId, version) => Tuple1(Artifact.MavenReference(groupId, artifactId, version))
  }

  val instantUnmarshaller: Unmarshaller[String, Instant] =
    // dataRaw is in seconds
    Unmarshaller.strict[String, Instant](dateRaw => Instant.ofEpochSecond(dateRaw.toLong))

  def paging(size: Int): Directive1[PageParams] =
    parameter("page".as[Int].withDefault(1)).map(PageParams(_, size))
}
