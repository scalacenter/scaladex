package ch.epfl.scala.index
package data

import model.misc.GithubRepo

import java.nio.file.Paths

package object github {
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
}
