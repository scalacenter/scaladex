package ch.epfl.scala.index
package maven

case class GithubRepo(username: String, repo: String)
object CleanUp {
  import fastparse.all._
  import fastparse.core.Parsed

  // More info in Rfc3986
  val Alpha = (CharIn('a' to 'z') | CharIn('A' to 'Z')).!
  val Digit = CharIn('0' to '9').!
  val Unreserved = P(Alpha | Digit | "-".! | ".".! | "_".! | "~".! ).!
  val Segment = P(Unreserved  | SubDelims | ":" | "@").!
  def SubDelims = CharIn("!$&'()*+,;=").!

  def removeDotGit(v: String) = 
    if(v.endsWith(".git")) v.dropRight(".git".length)
    else v
  
  val ScmUrl = P("scm:".? ~ "git:".? ~ ("git@" | "https://" | "git://" | "//") ~ 
                 "github.com" ~ (":" | "/") ~ Segment.rep.! ~ "/" ~ Segment.rep.!.map(removeDotGit))

  def parseRepo(v: String): Option[GithubRepo] = {
    ScmUrl.parse(v) match {
      case Parsed.Success((user, repo), _) => Some(GithubRepo(user.toLowerCase, repo.toLowerCase))
      case _ => None
    }
  }
}