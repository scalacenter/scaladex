package ch.epfl.scala.index
package data
package cleanup

import model.misc.GithubRepo
import maven.PomsReader

import org.json4s._
import org.json4s.native.Serialization.{read, writePretty}

import java.nio.file._
import java.nio.charset.StandardCharsets

import scala.util.Success
import scala.util.matching.Regex

class GithubRepoExtractor(paths: DataPaths) {
  case class Claims(claims: Map[String, Option[String]])
  object ClaimsSerializer
      extends CustomSerializer[Claims](
        format =>
          (
            {
              case JObject(obj) => {
                implicit val formats = DefaultFormats
                Claims(obj.map {
                  case (k, v) => (k, v.extract[Option[String]])
                }.toMap)
              }
            }, {
              case c: Claims =>
                JObject(
                  c.claims.toList.sorted.map {
                    case (k, v) =>
                      JField(k, v.map(s => JString(s)).getOrElse(JNull))
                  }
                )
            }
        ))

  /**
    * json4s formats
    */
  implicit private val formats = DefaultFormats ++ Seq(ClaimsSerializer)

  private val source = scala.io.Source.fromFile(paths.claims.toFile)
  private val claims = read[Claims](source.mkString).claims
  private def matches(m: Regex, s: String): Boolean = m.unapplySeq(s).isDefined

  val claimedRepos =
    claims.toList.sorted.flatMap { case (k, v) => v.map((k, _)) }.map {
      case (k, v) =>
        val List(groupId, artifactIdRawRegex) = k.split(" ").toList
        val artifactIdRegex =
          artifactIdRawRegex.replaceAllLiterally("*", "(.*)").r
        val matcher: (maven.ReleaseModel => Boolean) = pom => {
          def artifactMatches =
            artifactIdRawRegex == "*" ||
              matches(artifactIdRegex, pom.artifactId)

          def groupIdMaches = groupId == pom.groupId

          groupIdMaches && artifactMatches
        }

        val List(organization, repo) = v.split('/').toList

        (matcher, GithubRepo(organization, repo))
    }
  source.close()

  def apply(pom: maven.ReleaseModel): Option[GithubRepo] = {
    val fromPoms = pom.scm match {
      case Some(scm) => {
        List(scm.connection, scm.developerConnection, scm.url).flatten
          .flatMap(ScmInfoParser.parse)
          .filter(g => g.organization != "" && g.repository != "")
      }
      case None => List()
    }

    val fromClaims =
      claimedRepos.find { case (matcher, _) => matcher(pom) }.map {
        case (_, repo) => repo
      }

    /* use claims first because it can be used to rewrite scmInfo */
    val repo = fromClaims match {
      case None => fromPoms.headOption
      case s    => s
    }

    // scala xml interpolation is <url>{someVar}<url> and it's often wrong like <url>${someVar}<url>
    // after interpolation it look like <url>$thevalue<url>
    def fixInterpolationIssue(s: String): String = {
      if (s.startsWith("$")) s.drop(1) else s
    }

    repo.map {
      case GithubRepo(organization, repo) =>
        GithubRepo(
          fixInterpolationIssue(organization.toLowerCase),
          fixInterpolationIssue(repo.toLowerCase)
        )
    }
  }

  // script to generate contrib/claims.json
  def updateClaims(): Unit = {

    val poms =
      PomsReader.loadAll(paths).collect { case Success((pom, _, _)) => pom }

    val noUrl = poms.filter(pom => apply(pom).isEmpty)
    val notClaimed =
      noUrl.map(pom => (s"${pom.groupId} ${pom.artifactId}", None)).toMap
    val out = writePretty(Claims(notClaimed ++ claims))
      .replaceAllLiterally("\":\"", "\": \"") // make json breath
      .replaceAllLiterally("\":null", "\": null")

    Files.delete(paths.claims)
    Files.write(paths.claims, out.getBytes(StandardCharsets.UTF_8))

    ()
  }
}
