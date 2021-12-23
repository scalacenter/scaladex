package ch.epfl.scala.index
package data
package cleanup

import fastparse._
import scaladex.core.model.Project
import scaladex.core.util.Parsers

object ScmInfoParser extends Parsers {
  import fastparse.NoWhitespace._

  // More info in Rfc3986
  private def Unreserved[_: P] =
    P(Alpha | Digit | "-".! | ".".! | "_".! | "~".!).!
  private def Segment[_: P] = P(Unreserved | SubDelims | ":" | "@").!
  private def SubDelims[_: P] = CharIn("!$&'()*+,;=").!

  private def removeDotGit(v: String) =
    if (v.endsWith(".git")) v.dropRight(".git".length)
    else v

  private def ScmUrl[_: P] = P(
    "scm:".? ~ "git:".? ~ ("git@" | "https://" | "git://" | "//") ~
      "github.com" ~ (":" | "/") ~ Segment
        .rep(1)
        .! ~ "/" ~ Segment.rep(1).!.map(removeDotGit)
  )

  def parse(scmInfo: String): Option[Project.Reference] =
    fastparse.parse(scmInfo, x => ScmUrl(x)) match {
      case Parsed.Success((organization, repo), _) =>
        Some(Project.Reference.from(organization, repo))
      case _ => None
    }

}
