package ch.epfl.scala.index
package data
package github

import model.misc._

import org.json4s._
import org.json4s.native.Serialization.{read, writePretty}

import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets

import org.slf4j.LoggerFactory

import scala.util.{Try, Success}

/**
  * Github reader - to read all related infos from downloaded github files
  * and map / convert to GithubInfo object
  */
object GithubReader {

  private val log = LoggerFactory.getLogger(getClass)

  /**
    * read info from github files and convert to GithubInfo object
    * @param github
    * @return
    */
  def apply(paths: DataPaths, github: GithubRepo): Option[GithubInfo] = {

    info(paths, github).map { info =>
      val contributorList = contributors(paths, github).getOrElse(List())
      val topicsSet = topics(paths, github).getOrElse(List()).toSet
      val readmeStr = readme(paths, github).toOption
      info.copy(
        readme = readmeStr,
        contributors = contributorList,
        contributorCount = contributorList.size,
        commits = Some(contributorList.foldLeft(0)(_ + _.contributions)),
        topics = topicsSet,
        beginnerIssues = beginnerIssues(paths, github).getOrElse(List()),
        contributingGuide = contributingGuide(paths, github).getOrElse(None)
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
    slurp(readmePath).mkString(System.lineSeparator)
  }

  /**
    * read the main info from file if exists
    * @param github the git repo
    * @return
    */
  def info(paths: DataPaths, github: GithubRepo): Try[GithubInfo] = Try {

    import Json4s._

    val repoInfoPath = githubRepoInfoPath(paths, github)
    val repository = read[Repository](slurp(repoInfoPath))

    GithubInfo(
      name = repository.name,
      owner = repository.owner.login,
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
    * extract the contributors if they exist
    * @param github the current repo
    * @return
    */
  def contributors(paths: DataPaths,
                   github: GithubRepo): Try[List[GithubContributor]] = Try {

    import Json4s._

    val repoInfoPath = githubRepoContributorsPath(paths, github)
    val repository = read[List[Contributor]](slurp(repoInfoPath))
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

    import Json4s._

    val repoTopicsPath = githubRepoTopicsPath(paths, github)
    val graphqlResult = read[GraphqlResult](slurp(repoTopicsPath))
    val graphqlTopics = for {
      data <- graphqlResult.data;
      repo <- data.repository;
      topics <- repo.repositoryTopics;
      nodes <- topics.nodes
    } yield { nodes }
    graphqlTopics.getOrElse(List()).map(_.topic.flatMap(_.name).getOrElse(""))
  }

  /**
    * extract the issues if they exist
    * @param github the current repo
    * @return
    */
  def beginnerIssues(paths: DataPaths,
                     github: GithubRepo): Try[List[GithubIssue]] = Try {

    import Json4s._

    val issuesPath = githubRepoIssuesPath(paths, github)
    val graphqlResult = read[GraphqlResult](slurp(issuesPath))
    val graphqlIssues = for {
      data <- graphqlResult.data;
      repo <- data.repository;
      issues <- repo.issues;
      nodes <- issues.nodes
    } yield { nodes }
    graphqlIssues.getOrElse(List()).map(issue =>
      GithubIssue(
        issue.number.getOrElse(0),
        issue.title.getOrElse(""),
        issue.bodyText.getOrElse(""),
        Url(issue.url.getOrElse(""))
      )
    )
  }

  /**
    * read the contributing guide from file if exists
    * @param github the git repo
    * @return
    */
  def contributingGuide(paths: DataPaths, github: GithubRepo): Try[Option[Url]] = Try {

    import Json4s._

    val communityProfilePath = githubRepoCommunityProfilePath(paths, github)
    val communityProfile =
      read[CommunityProfile](slurp(communityProfilePath))
    communityProfile.files.contributing.html_url.map(Url(_))
  }

  /**
    * get the chatroom from the readme if it exists
    * @param readme the readme text
    * @return
    */
  def chatroom(repo: GithubRepo, readme: String): Option[Url] = {

    val gitterPattern = """<a href="(https?://gitter.im/[^"]*)"""".r
    gitterPattern
      .findFirstMatchIn(readme)
      .map(x => Url(x.group(1)))
  }

  case class Moved(inner: Map[GithubRepo, GithubRepo])
  object Moved {
    object MovedSerializer
        extends CustomSerializer[Moved](
          format =>
            (
              {
                case JObject(obj) => {
                  implicit val formats = DefaultFormats

                  Moved(
                    obj.map {
                      case (k, JString(v)) => {
                        val List(sourceOwner, sourceRepo) = k.split('/').toList
                        val List(destinationOwner, destinationRepo) =
                          v.split('/').toList

                        (
                          GithubRepo(sourceOwner, sourceRepo),
                          GithubRepo(destinationOwner, destinationRepo)
                        )
                      }
                      case e => {
                        sys.error("cannot read: " + e)
                      }
                    }.toMap
                  )
                }
              }, {
                case m: Moved =>
                  JObject(
                    m.inner.toList.sorted.map {
                      case (GithubRepo(sourceOwner, sourceRepo),
                            GithubRepo(destinationOwner, destinationRepo)) =>
                        JField(
                          s"$sourceOwner/$sourceRepo",
                          JString(s"$destinationOwner/$destinationRepo")
                        )
                    }
                  )
              }
          ))

    implicit val formats = DefaultFormats ++ Seq(MovedSerializer)
  }

  def movedRepositories(paths: DataPaths): Map[GithubRepo, GithubRepo] = {
    import Moved.formats
    read[Moved](slurp(paths.movedGithub)).inner
  }

  /**
    * keep track of repository remaning/tranfers
    *
    */
  def appendMovedRepository(paths: DataPaths, repo: GithubRepo): Unit = {
    import Moved.formats
    info(paths, repo) match {
      case Success(info) => {
        val source = repo
        val destination =
          GithubRepo(info.owner.toLowerCase, info.name.toLowerCase)

        if (source != destination) {
          val moved = movedRepositories(paths)
          val movedUpdated = moved.updated(source, destination)

          if (moved != movedUpdated) {
            Files.write(
              paths.movedGithub,
              writePretty(Moved(movedUpdated)).getBytes(StandardCharsets.UTF_8)
            )
          }
        }
      }
      case _ => log.warn(s"cannot read repo info: $repo")
    }
  }

  private def slurp(path: Path): String =
    Files.readAllLines(path).toArray.mkString("")

}
