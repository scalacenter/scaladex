package ch.epfl.scala.index
package cleanup

object ScmCleanup {
  import fastparse.all._
  import fastparse.core.Parsed

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

  def parseRepo(v: String): Option[GithubRepo] = {
    ScmUrl.parse(v) match {
      case Parsed.Success((user, repo), _) => Some(GithubRepo(user.toLowerCase, repo.toLowerCase))
      case _ => None
    }
  }
}