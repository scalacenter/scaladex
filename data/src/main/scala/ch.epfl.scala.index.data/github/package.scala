package ch.epfl.scala.index
package data

import model.Parsers
import model.misc.GithubRepo

import java.nio.file.Paths

package object github extends Parsers {
  private[github] val githubBase = build.info.BuildInfo.baseDirectory.toPath
    .resolve(Paths.get("index", "github"))
  private[github] def path(github: GithubRepo) = {
    val GithubRepo(user, repo) = github
    githubBase.resolve(Paths.get(user, repo))
  }
  def githubReadmePath(github: GithubRepo) =
    path(github).resolve(Paths.get("README.html"))
  def githubRepoInfoPath(github: GithubRepo) =
    path(github).resolve(Paths.get("repo.json"))
  def githubRepoIssuesPath(github: GithubRepo) =
    path(github).resolve(Paths.get("issues.json"))
  def githubRepoContributorsPath(github: GithubRepo) =
    path(github).resolve(Paths.get("contributors.json"))

  /**
    * extracts the last page from a given link string
    * - <https://api.github.com/repositories/130013/issues?page=2>; rel="next", <https://api.github.com/repositories/130013/issues?page=23>; rel="last"
    * - <https://api.github.com/user/repos?page=2>; rel=next, <https://api.github.com/user/repos?page=2>; rel=last
    * @param links the links
    * @return
    */
  def extractLastPage(links: String): Int = {
    val pattern = """page=([0-9]+)>; rel=["]?([a-z]+)["]?""".r
    val pages = pattern
      .findAllIn(links)
      .matchData
      .map(x => x.group(2) -> x.group(1).toInt)
      .toMap
    pages.getOrElse("last", 1)
  }
}
