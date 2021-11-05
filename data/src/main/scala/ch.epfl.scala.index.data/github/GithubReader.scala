package ch.epfl.scala.index
package data
package github

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import scala.reflect.Manifest
import scala.util.Success
import scala.util.Try

import ch.epfl.scala.index.model.misc._
import ch.epfl.scala.services.storage.DataPaths
import org.json4s._
import org.json4s.native.Serialization.writePretty
import org.json4s.native.Serialization.{read => parse}
import org.slf4j.LoggerFactory

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
  def apply(paths: DataPaths, github: GithubRepo): Option[GithubInfo] =
    info(paths, github).map { info =>
      val contributorList = contributors(paths, github).getOrElse(List())
      info.copy(
        readme = readme(paths, github).toOption, // README.html
        contributors = contributorList, // contributors.json
        contributorCount = contributorList.size, // contributors.json
        commits = Some(
          contributorList.foldLeft(0)(_ + _.contributions)
        ), // contributors.json
        topics = topics(paths, github).getOrElse(List()).toSet, // topics.json
        beginnerIssues = beginnerIssues(paths, github).getOrElse(List()), // issues.json
        contributingGuide = contributingGuide(paths, github).getOrElse(None), // community.json
        codeOfConduct = codeOfConduct(paths, github).getOrElse(None), // community.json
        chatroom = chatroom(paths, github).toOption // chatroom.txt
      )
    }.toOption

  /**
   * read the readme file if exists
   * @param github the git repo
   * @return
   */
  def readme(paths: DataPaths, github: GithubRepo): Try[String] = Try {

    val readmePath = githubReadmePath(paths, github)
    slurp(readmePath)
  }

  /**
   * read the main info from file if exists
   * @param github the git repo
   * @return
   */
  def info(paths: DataPaths, github: GithubRepo): Try[GithubInfo] = Try {

    import Json4s._

    val repoInfoPath = githubRepoInfoPath(paths, github)
    val repository = read[V3.Repository](repoInfoPath)

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
  def contributors(
      paths: DataPaths,
      github: GithubRepo
  ): Try[List[GithubContributor]] = Try {

    import Json4s._

    val repoInfoPath = githubRepoContributorsPath(paths, github)
    val repository = read[List[V3.Contributor]](repoInfoPath)
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
    val graphqlResult = read[V4.RepositoryResult](repoTopicsPath)
    val graphqlTopics = for {
      data <- graphqlResult.data
      repo <- data.repository
      topics <- repo.repositoryTopics
      nodes <- topics.nodes
    } yield nodes

    graphqlTopics.getOrElse(List()).map(_.topic.flatMap(_.name).getOrElse(""))
  }

  /**
   * extract the issues if they exist
   * @param github the current repo
   * @return
   */
  def beginnerIssues(
      paths: DataPaths,
      github: GithubRepo
  ): Try[List[GithubIssue]] = Try {

    import Json4s._

    val issuesPath = githubRepoIssuesPath(paths, github)
    val graphqlResult = read[V4.RepositoryResult](issuesPath)

    val graphqlIssues = for {
      data <- graphqlResult.data
      repo <- data.repository
      issues <- repo.issues
      nodes <- issues.nodes
    } yield nodes

    graphqlIssues
      .getOrElse(List())
      .map(issue =>
        GithubIssue(
          issue.number,
          issue.title,
          Url(issue.url)
        )
      )
  }

  /**
   * read the link to the contributing guide from file if exists
   * @param github the git repo
   * @return
   */
  def contributingGuide(
      paths: DataPaths,
      github: GithubRepo
  ): Try[Option[Url]] = Try {

    import Json4s._

    val communityProfilePath = githubRepoCommunityProfilePath(paths, github)
    val communityProfile =
      read[V3.CommunityProfile](communityProfilePath)
    communityProfile.files.contributing.html_url.map(Url(_))
  }

  /**
   * read the link to the code of conduct from file if exists
   * @param github the git repo
   * @return
   */
  def codeOfConduct(paths: DataPaths, github: GithubRepo): Try[Option[Url]] =
    Try {

      import Json4s._

      val communityProfilePath = githubRepoCommunityProfilePath(paths, github)
      val communityProfile =
        read[V3.CommunityProfile](communityProfilePath)
      communityProfile.files.code_of_conduct.html_url.map(Url(_))
    }

  /**
   * read the link to the chatroom from file if it exists
   * @return
   */
  def chatroom(paths: DataPaths, github: GithubRepo): Try[Url] = Try {

    val chatroomPath = githubRepoChatroomPath(paths, github)
    Url(slurp(chatroomPath))
  }

  case class Moved(inner: Map[GithubRepo, GithubRepo])
  object Moved {
    object MovedSerializer
        extends CustomSerializer[Moved](format =>
          (
            {
              case JObject(obj) =>
                implicit val formats = DefaultFormats

                Moved(
                  obj.map {
                    case (k, JString(v)) =>
                      val List(sourceOwner, sourceRepo) = k.split('/').toList
                      val List(destinationOwner, destinationRepo) =
                        v.split('/').toList

                      (
                        GithubRepo(sourceOwner, sourceRepo),
                        GithubRepo(destinationOwner, destinationRepo)
                      )
                    case e =>
                      sys.error("cannot read: " + e)
                  }.toMap
                )
            },
            {
              case m: Moved =>
                JObject(
                  m.inner.toList.sorted.map {
                    case (
                          GithubRepo(sourceOwner, sourceRepo),
                          GithubRepo(destinationOwner, destinationRepo)
                        ) =>
                      JField(
                        s"$sourceOwner/$sourceRepo",
                        JString(s"$destinationOwner/$destinationRepo")
                      )
                  }
                )
            }
          )
        )

    implicit val formats: Formats = DefaultFormats ++ Seq(MovedSerializer)
  }

  def movedRepositories(paths: DataPaths): Map[GithubRepo, GithubRepo] = {
    import Moved.formats
    read[Moved](paths.movedGithub).inner
  }

  /**
   * keep track of repository remaning/tranfers
   */
  def appendMovedRepository(paths: DataPaths, repo: GithubRepo): Unit = {
    import Moved.formats
    info(paths, repo) match {
      case Success(info) =>
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
      case _ => log.warn(s"cannot read repo info: $repo")
    }
  }

  private def read[T: Manifest](path: Path)(implicit formats: Formats): T =
    parse[T](slurp(path))
}
