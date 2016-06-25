package ch.epfl.scala.index
package data
package github

import org.json4s.native.Serialization.read

import scala.util.Try
import java.nio.file.Files

import ch.epfl.scala.index.model.misc.{GithubInfo, GithubRepo, Url}

object GithubReader {
  def apply(github: GithubRepo): Option[GithubInfo] = {

    info(github).map { info =>

      info.copy(readme = readme(github).toOption)
    }.toOption
  }


  def readme(github: GithubRepo): Try[String] = Try {

    val readmePath = githubReadmePath(github)
    Files.readAllLines(readmePath).toArray.mkString(System.lineSeparator)
  }

  def info(github: GithubRepo): Try[GithubInfo] = Try {

    val repoInfoPath = githubRepoInfoPath(github)
    val repository = read[Repository](Files.readAllLines(repoInfoPath).toArray.mkString(""))
    GithubInfo(
      homepage = repository.homepage.map(h => Url(h)),
      description = Some(repository.description),
      logo = repository.organization.map(o => Url(o.avatar_url)),
      stars = Some(repository.stargazers_count),
      forks = Some(repository.forks)
    )
  }
}