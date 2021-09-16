package ch.epfl.scala.index
package data

import java.nio.file.Path
import java.nio.file.Paths

import ch.epfl.scala.index.model.DataPaths
import ch.epfl.scala.index.model.Parsers
import ch.epfl.scala.index.model.misc.GithubRepo

package object github extends Parsers {
  private[github] def path(paths: DataPaths, github: GithubRepo) = {
    val GithubRepo(user, repo) = github
    paths.github.resolve(Paths.get(user, repo))
  }
  def githubReadmePath(paths: DataPaths, github: GithubRepo): Path =
    path(paths, github).resolve(Paths.get("README.html"))

  def githubRepoInfoPath(paths: DataPaths, github: GithubRepo): Path =
    path(paths, github).resolve(Paths.get("repo.json"))

  def githubRepoIssuesPath(paths: DataPaths, github: GithubRepo): Path =
    path(paths, github).resolve(Paths.get("issues.json"))

  def githubRepoContributorsPath(paths: DataPaths, github: GithubRepo): Path =
    path(paths, github).resolve(Paths.get("contributors.json"))

  def githubRepoTopicsPath(paths: DataPaths, github: GithubRepo): Path =
    path(paths, github).resolve(Paths.get("topics.json"))

  def githubRepoCommunityProfilePath(
      paths: DataPaths,
      github: GithubRepo
  ): Path =
    path(paths, github).resolve(Paths.get("community.json"))

  def githubRepoChatroomPath(paths: DataPaths, github: GithubRepo): Path =
    path(paths, github).resolve(Paths.get("chatroom.txt"))

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
