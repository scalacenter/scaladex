package scaladex.data
package cleanup

import scala.annotation.nowarn
import scala.io.Source
import scala.util.Using
import scala.util.matching.Regex

import scaladex.core.model.Project
import scaladex.infra.DataPaths

import org.json4s.JsonAST.JField
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.json4s.*
import org.json4s.native.Serialization

class GithubRepoExtractor(paths: DataPaths):
  object ClaimSerializer extends CustomSerializer[Claims](_ => (serialize, deserialize))
  given Formats = DefaultFormats ++ Seq(ClaimSerializer)

  case class Claim(pattern: String, repo: String)
  case class Claims(claims: Seq[Claim])

  // repository for the not claimed projects
  private final val void = "scalacenter/scaladex-void"

  private def matches(m: Regex, s: String): Boolean = m.unapplySeq(s).isDefined
  @nowarn("cat=deprecation")
  private val claims =
    Using.resource(Source.fromFile(paths.claims.toFile)) { source =>
      // when the repository is void, the project is not claimed
      Serialization.read[Claims](source.mkString).claims.filter(_.repo != void)
    }

  private val claimedRepos = claims
    .map { claim =>
      val List(groupId, artifactIdRawRegex) = claim.pattern.split(" ").toList
      val artifactIdRegex =
        artifactIdRawRegex.replace("*", "(.*)").r
      val matcher: maven.ArtifactModel => Boolean = pom =>
        def artifactMatches =
          artifactIdRawRegex == "*" ||
            matches(artifactIdRegex, pom.artifactId)

        def groupIdMaches = groupId == pom.groupId

        groupIdMaches && artifactMatches

      val List(organization, repo) = claim.repo.split('/').toList

      (matcher, Project.Reference.from(organization, repo))
    }

  def extract(pom: maven.ArtifactModel): Option[Project.Reference] =
    val fromPoms = pom.scm match
      case Some(scm) =>
        List(scm.connection, scm.developerConnection, scm.url).flatten
          .flatMap(ScmInfoParser.parse)
          .filter(g => !g.organization.isEmpty() && !g.repository.isEmpty())
      case None => List()

    val fromClaims =
      claimedRepos.find { case (matcher, _) => matcher(pom) }.map { case (_, repo) => repo }

    /* use claims first because it can be used to rewrite scmInfo */
    val repo = fromClaims.orElse(fromPoms.headOption)

    // scala xml interpolation is <url>{someVar}<url> and it's often wrong like <url>${someVar}<url>
    // after interpolation it look like <url>$thevalue<url>
    def fixInterpolationIssue(s: String): String =
      if s.startsWith("$") then s.drop(1) else s

    repo.map {
      case Project.Reference(organization, repo) =>
        Project.Reference.from(
          fixInterpolationIssue(organization.value),
          fixInterpolationIssue(repo.value)
        )
    }
  end extract

  @nowarn("cat=deprecation")
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
end GithubRepoExtractor
