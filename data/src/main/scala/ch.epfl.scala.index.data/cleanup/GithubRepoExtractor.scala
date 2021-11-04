package ch.epfl.scala.index
package data
package cleanup

import java.nio.charset.StandardCharsets
import java.nio.file._

import scala.io.Source
import scala.util.Using
import scala.util.matching.Regex

import ch.epfl.scala.index.data.maven.PomsReader
import ch.epfl.scala.index.model.misc.GithubRepo
import org.json4s.CustomSerializer
import org.json4s.DefaultFormats
import org.json4s.Formats
import org.json4s.JValue
import org.json4s.JsonAST.JField
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.json4s.native.Serialization.read
import org.json4s.native.Serialization.writePretty

class GithubRepoExtractor(paths: DataPaths) {
  object ClaimSerializer extends CustomSerializer[Claims](_ => (serialize, deserialize))
  implicit val formats: Formats = DefaultFormats ++ Seq(ClaimSerializer)

  case class Claim(pattern: String, repo: String)
  case class Claims(claims: Seq[Claim])

  // repository for the not claimed projects
  private final val void = "scalacenter/scaladex-void"

  private def matches(m: Regex, s: String): Boolean = m.unapplySeq(s).isDefined
  private val claims =
    Using.resource(Source.fromFile(paths.claims.toFile)) { source =>
      read[Claims](source.mkString).claims
        .filter(
          _.repo != void
        ) // when the repository is void, the project is not claimed
    }

  private val claimedRepos = claims
    .map { claim =>
      val List(groupId, artifactIdRawRegex) = claim.pattern.split(" ").toList
      val artifactIdRegex =
        artifactIdRawRegex.replace("*", "(.*)").r
      val matcher: maven.ReleaseModel => Boolean = pom => {
        def artifactMatches =
          artifactIdRawRegex == "*" ||
            matches(artifactIdRegex, pom.artifactId)

        def groupIdMaches = groupId == pom.groupId

        groupIdMaches && artifactMatches
      }

      val List(organization, repo) = claim.repo.split('/').toList

      (matcher, GithubRepo(organization, repo))
    }

  private val movedRepositories = github.GithubReader.movedRepositories(paths)

  def apply(pom: maven.ReleaseModel): Option[GithubRepo] = {
    val fromPoms = pom.scm match {
      case Some(scm) =>
        List(scm.connection, scm.developerConnection, scm.url).flatten
          .flatMap(ScmInfoParser.parse)
          .filter(g => g.organization != "" && g.repository != "")
      case None => List()
    }

    val fromClaims =
      claimedRepos.find { case (matcher, _) => matcher(pom) }.map { case (_, repo) => repo }

    /* use claims first because it can be used to rewrite scmInfo */
    val repo = fromClaims.orElse(fromPoms.headOption)

    // scala xml interpolation is <url>{someVar}<url> and it's often wrong like <url>${someVar}<url>
    // after interpolation it look like <url>$thevalue<url>
    def fixInterpolationIssue(s: String): String =
      if (s.startsWith("$")) s.drop(1) else s

    repo.map {
      case GithubRepo(organization, repo) =>
        val repo2 =
          GithubRepo(
            fixInterpolationIssue(organization.toLowerCase),
            fixInterpolationIssue(repo.toLowerCase)
          )

        movedRepositories.getOrElse(repo2, repo2)
    }
  }

  // script to generate contrib/claims.json
  def updateClaims(): Unit = {
    val poms =
      PomsReader.loadAll(paths).map { case (pom, _, _) => pom }

    val notClaimed = poms
      .filter(pom => apply(pom).isEmpty)
      .map(pom => s"${pom.groupId} ${pom.artifactId}")
      .toSeq
      .distinct
      .map(Claim(_, void))
    val out = writePretty(Claims(notClaimed ++ claims))
      .replace("\":\"", "\": \"") // make json breath

    Files.delete(paths.claims)
    Files.write(paths.claims, out.getBytes(StandardCharsets.UTF_8))

    ()
  }

  private def serialize: PartialFunction[JValue, Claims] = {
    case JObject(obj) =>
      val claims = obj.map { case (k, v) => Claim(k, v.extract[String]) }
      Claims(claims)
  }

  private def deserialize: PartialFunction[Any, JValue] = {
    case Claims(claims) =>
      val fields = claims.sortBy(_.pattern).map(claim => JField(claim.pattern, JString(claim.repo)))
      JObject(fields.toList)
  }
}
