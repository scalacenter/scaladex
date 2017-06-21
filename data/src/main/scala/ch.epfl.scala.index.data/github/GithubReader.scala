package ch.epfl.scala.index
package data
package github

import ch.epfl.scala.index.model.misc._
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
  def apply(paths: DataPaths, github: GithubRepo): Option[GithubInfo] = {

    info(paths, github).map { info =>
      val contributorList = contributors(paths, github).getOrElse(List())
      val topicsSet = topics(paths, github).getOrElse(List()).toSet
      info.copy(
        readme = readme(paths, github).toOption,
        contributors = contributorList,
        contributorCount = contributorList.size,
        commits = Some(contributorList.foldLeft(0)(_ + _.contributions)),
        topics = topicsSet
      )
    }.toOption
  }

  /**
    * read the readme file if exists
    * @param github the git repo
    * @return
    */
  def readme(paths: DataPaths, github: GithubRepo): Try[String] = Try {

    val readmePath = githubReadmePath(paths, github)
    Files.readAllLines(readmePath).toArray.mkString(System.lineSeparator)
  }

  /**
    * read the main info from file if exists
    * @param github the git repo
    * @return
    */
  def info(paths: DataPaths, github: GithubRepo): Try[GithubInfo] = Try {

    val repoInfoPath = githubRepoInfoPath(paths, github)
    val repository =
      read[Repository](Files.readAllLines(repoInfoPath).toArray.mkString(""))
    GithubInfo(
      homepage = repository.homepage.map(h => Url(h)),
      description = repository.description,
      logo = Some(Url(repository.owner.avatar_url)),
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
  def contributors(paths: DataPaths,
                   github: GithubRepo): Try[List[GithubContributor]] = Try {

    val repoInfoPath = githubRepoContributorsPath(paths, github)
    val repository = read[List[Contributor]](
      Files.readAllLines(repoInfoPath).toArray.mkString(""))
    repository.map { contributor =>
      GithubContributor(
        contributor.login,
        contributor.avatar_url,
        Url(contributor.html_url),
        contributor.contributions
      )
    }
  }

  /**
    * extract the topics if they exist
    * @param github the current repo
    * @return
    */
  def topics(paths: DataPaths, github: GithubRepo): Try[List[String]] = Try {

    val repoTopicsPath = githubRepoTopicsPath(paths, github)
    val graphqlResult = read[GraphqlResult](
      Files.readAllLines(repoTopicsPath).toArray.mkString(""))
    graphqlResult.data.repository.repositoryTopics.nodes.map { node =>
      node.topic.name
    }
  }
}
