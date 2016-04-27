package ch.epfl.scala.index
package cleanup

import scala.util._
import spray.json._
import java.nio.file._

import fastparse.all._
import fastparse.core.Parsed

object ScmCleanup extends DefaultJsonProtocol {
  private val file = Paths.get("..", "..", "contrib", "claims.json").toFile
  private val source = scala.io.Source.fromFile(file)
  private val claims = source.mkString.parseJson.convertTo[Map[String, Option[String]]].toList.sorted
  private val matchers = claims.
    map{case (k, v) => v.map((k, _))}.flatten.
    map{case (k, v) =>
      val regex = k.replaceAllLiterally("*", "(.*)").r
      val List(user, repo) = v.split('/').toList
      (regex, GithubRepo(user, repo))
    }
  source.close()

  // More info in Rfc3986
  private val Alpha = (CharIn('a' to 'z') | CharIn('A' to 'Z')).!
  private val Digit = CharIn('0' to '9').!
  private val Unreserved = P(Alpha | Digit | "-".! | ".".! | "_".! | "~".! ).!
  private val Segment = P(Unreserved  | SubDelims | ":" | "@").!
  private def SubDelims = CharIn("!$&'()*+,;=").!

  private def removeDotGit(v: String) = 
    if(v.endsWith(".git")) v.dropRight(".git".length)
    else v
  
  private val ScmUrl = P("scm:".? ~ "git:".? ~ ("git@" | "https://" | "git://" | "//") ~ 
                 "github.com" ~ (":" | "/") ~ Segment.rep.! ~ "/" ~ Segment.rep.!.map(removeDotGit))

  private def parseRepo(v: String): Option[GithubRepo] = {
    ScmUrl.parse(v) match {
      case Parsed.Success((user, repo), _) => Some(GithubRepo(user.toLowerCase, repo.toLowerCase))
      case _ => None
    }
  }

  def find(d: maven.MavenModel): Set[GithubRepo] = {
    import d._
    def matches(m: matching.Regex, s: String): Boolean =
       m.unapplySeq(s).isDefined

    val fromPoms =
      scm match {
        case Some(scmV) => {
          import scmV._
          List(connection, developerConnection, url).
            flatten.
            flatMap(cleanup.ScmCleanup.parseRepo).
            filter(g => g.user != "" && g.repo != "")
        }
        case None => List()
      }
    
    val fromClaims =
      matchers.find{case (m, _) => 
        matches(m, s"$groupId $artifactId $version")
      }.map(_._2).toList
    
    (fromPoms ++ fromClaims).toSet
  }

  // script to generate contrib/claims.json
  def run() = {
    import scala.util._
    val poms = maven.Poms.get.collect{ case Success(p) => maven.PomConvert(p) }
    val noUrl = poms.filter(p => find(p).size == 0)
    val notClaimed = noUrl.map{d =>
        import d._
        (s"$groupId $artifactId $version", None)
      }.toMap
    
    val nl = System.lineSeparator

    (notClaimed ++ claims).toList.sorted.map{ case (k, v) =>
      "  \"" + k + "\": " + v.map(x => "\"" + x + "\"").getOrElse("null")
    }.mkString("{" + nl, "," + nl , nl + "}")
  }
}