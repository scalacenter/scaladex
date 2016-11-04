package ch.epfl.scala.index
package data
package cleanup

import model.misc.GithubRepo
import model.Parsers

import fastparse.all._
import fastparse.core.Parsed

object ScmInfoParser extends Parsers {
  // More info in Rfc3986
  private val Unreserved = P(Alpha | Digit | "-".! | ".".! | "_".! | "~".!).!
  private val Segment = P(Unreserved | SubDelims | ":" | "@").!
  private def SubDelims = CharIn("!$&'()*+,;=").!

  private def removeDotGit(v: String) =
    if (v.endsWith(".git")) v.dropRight(".git".length)
    else v

  private val ScmUrl = P(
    "scm:".? ~ "git:".? ~ ("git@" | "https://" | "git://" | "//") ~
      "github.com" ~ (":" | "/") ~ Segment.rep.! ~ "/" ~ Segment.rep.!.map(removeDotGit))

  def parse(scmInfo: String): Option[GithubRepo] = {
    ScmUrl.parse(scmInfo) match {
      case Parsed.Success((organization, repo), _) =>
        Some(GithubRepo(organization, repo))
      case _ => None
    }
  }

}
