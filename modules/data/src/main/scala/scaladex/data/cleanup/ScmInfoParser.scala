package scaladex.data
package cleanup

import scaladex.core.model.Project
import scaladex.core.util.Parsers

import fastparse.*

object ScmInfoParser extends Parsers:
  import fastparse.NoWhitespace.*

  // More info in Rfc3986
  private def Unreserved[A: P] =
    P(Alpha | Digit | "-".! | ".".! | "_".! | "~".!).!
  private def Segment[A: P] = P(Unreserved | SubDelims | ":" | "@").!
  private def SubDelims[A: P] = CharIn("!$&'()*+,;=").!

  private def removeDotGit(v: String) =
    if v.endsWith(".git") then v.dropRight(".git".length)
    else v

  private def ScmUrl[A: P] = P(
    // "git:" is optional to handle "scm:git@github.com:..." format
    "scm:".? ~ "git:".? ~ ("git@" | "https://" | "git://" | ("ssh://" ~ "git@".?) | "//") ~
      "github.com" ~ (":" | "/") ~ Segment
        .rep(1)
        .! ~ "/" ~ Segment.rep(1).!.map(removeDotGit)
  )

  def parseRawConnection(scmInfo: String): Option[Project.Reference] =
    fastparse.parse(scmInfo, x => ScmUrl(x)) match
      case Parsed.Success((organization, repo), _) =>
        Some(Project.Reference.from(organization, repo))
      case _ => None
end ScmInfoParser
