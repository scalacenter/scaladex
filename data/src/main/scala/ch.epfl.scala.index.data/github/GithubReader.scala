package ch.epfl.scala.index
package data
package github

import model.misc.{GithubContributor, GithubInfo, GithubRepo, Url}

import org.json4s.native.Serialization.read

import java.nio.file.Files
import scala.util.Try

/**
 * Github reader - to read all related infos from downloaded github files
 * and map / convert to GithubInfo object
 */
object GithubReader {

  import Json4s._

  /**
   * read info from github files and convert to GithubInfo object
   * @param github
   * @return
   */
  def apply(github: GithubRepo): Option[GithubInfo] = {

    info(github).map { info =>

      val contributorList = contributors(github).getOrElse(List())
      info.copy(
        readme = readme(github).toOption,
        contributors = contributorList,
        commits = Some(contributorList.foldLeft(0)(_ + _.contributions))
      )
    }.toOption
  }

  /**
   * read the readme file if exists
   * @param github the git repo
   * @return
   */
  def readme(github: GithubRepo): Try[String] = Try {

    val readmePath = githubReadmePath(github)
    Files.readAllLines(readmePath).toArray.mkString(System.lineSeparator)
  }

  /**
   * read the main info from file if exists
   * @param github the git repo
   * @return
   */
  def info(github: GithubRepo): Try[GithubInfo] = Try {

    val repoInfoPath = githubRepoInfoPath(github)
    val repository = read[Repository](Files.readAllLines(repoInfoPath).toArray.mkString(""))
    GithubInfo(
      homepage = repository.homepage.map(h => Url(h)),
      description = Some(repository.description),
      logo = repository.organization.map(o => Url(o.avatar_url)),
      stars = Some(repository.stargazers_count),
      forks = Some(repository.forks),
      watchers = Some(repository.subscribers_count),
      issues = Some(repository.open_issues)
    )
  }

  /**
   * extract the contributors of exists
   * @param github the current repo
   * @return
   */
  def contributors(github: GithubRepo): Try[List[GithubContributor]] = Try {

    val repoInfoPath = githubRepoContributorsPath(github)
    val repository = read[List[Contributor]](Files.readAllLines(repoInfoPath).toArray.mkString(""))
    repository.map { contributor =>
      GithubContributor(
        contributor.login,
        contributor.avatar_url,
        Url(contributor.html_url),
        contributor.contributions
      )
    }
  }
}