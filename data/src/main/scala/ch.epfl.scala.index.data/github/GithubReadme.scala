package ch.epfl.scala.index
package data
package github

import model.GithubRepo

private [github] object GithubReadme {
  def absoluteUrl(readmeHtml: String, githubRepo: GithubRepo, defaultBranch: String): String = {
    val GithubRepo(user, repo) = githubRepo
    import org.jsoup.Jsoup
    
    // github returns absolute url we need a "safe way" to replace the
    val someUrl = "http://NTU1ZTAwY2U2YTljZGZjOTYyYjg5NGZh.com"

    val doc = Jsoup.parse(readmeHtml, someUrl)

    val root = s"https://github.com/$user/$repo"
    def base(v: String) = s"$root/$v/$defaultBranch"
    val raw = base("raw")
    val blob = base("blob")
    
    doc.select("a, img").toArray
      .map(_.asInstanceOf[org.jsoup.nodes.Element])
      .foreach{e => 
        val (at, replace) =
          if(e.tagName == "a") {
            val attr = "href"
            val href = 
              if(e.attr(attr).startsWith("#")) root
              else blob

            e.attr("target", "_blank")
            (attr, href)
          }
          else ("src", raw)
        
        e.attr(at, e.absUrl(at).replaceAllLiterally(someUrl, replace))
      }

    doc.body.childNodes.toArray.mkString("")
  }
}