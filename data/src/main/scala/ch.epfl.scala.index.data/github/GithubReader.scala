package ch.epfl.scala.index
package data
package github

import org.json4s.native.Serialization.read

import scala.util.Try
import java.nio.file.Files

import ch.epfl.scala.index.model.misc.{GithubInfo, GithubRepo, Url}

object GithubReader {
  def apply(github: GithubRepo): Option[GithubInfo] = {
    val readmePath = githubReadmePath(github)
    val readmeHtml =
      if(Files.exists(readmePath)) Try(Files.readAllLines(readmePath).toArray.mkString(System.lineSeparator)).toOption
      else None

    val repoInfoPath = githubRepoInfoPath(github)
    val repo =
      if(Files.exists(repoInfoPath)) {
        import Json4s._
        
        Try(Files.readAllLines(repoInfoPath).toArray.mkString("")).flatMap(info =>
          Try(read[Repository](info))
        ).toOption
      } else None

    convert(readmeHtml, repo)
  }

  private def convert(readmeHtml: Option[String], repo: Option[Repository]): Option[GithubInfo] = {
    if(readmeHtml.isEmpty && repo.isEmpty) None
    else {
      val info  = GithubInfo(readme = readmeHtml)
      Some(repo.fold(info)( r =>
        info.copy(
          homepage = r.homepage.map(h => Url(h)),
          description = Some(r.description),
          logo = r.organization.map(o => Url(o.avatar_url)),
          stars = Some(r.stargazers_count),
          forks = Some(r.forks)
        )
      ))
    }
  }
}