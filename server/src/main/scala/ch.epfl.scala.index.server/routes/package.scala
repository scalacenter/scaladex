package ch.epfl.scala.index.server

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher1
import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.misc.GithubIssue
import ch.epfl.scala.index.model.misc.SearchParams
import ch.epfl.scala.index.model.misc.Url
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewRelease

package object routes {
  val organizationM: PathMatcher1[NewProject.Organization] =
    Segment.map(NewProject.Organization)
  val repositoryM: PathMatcher1[NewProject.Repository] =
    Segment.map(NewProject.Repository)
  val artifactM: PathMatcher1[NewRelease.ArtifactName] =
    Segment.map(NewRelease.ArtifactName)
  val versionM: PathMatcher1[SemanticVersion] =
    Segment.flatMap(SemanticVersion.tryParse)

  def searchParams(user: Option[UserState]): Directive1[SearchParams] =
    parameters(
      (
        "q" ? "*",
        "page".as[Int] ? 1,
        "sort".?,
        "topics".as[String].*,
        "targetTypes".as[String].*,
        "scalaVersions".as[String].*,
        "scalaJsVersions".as[String].*,
        "scalaNativeVersions".as[String].*,
        "sbtVersions".as[String].*,
        "you".?,
        "contributingSearch".as[Boolean] ? false
      )
    ).tmap {
      case (
            q,
            page,
            sort,
            topics,
            targetTypes,
            scalaVersions,
            scalaJsVersions,
            scalaNativeVersions,
            sbtVersions,
            you,
            contributingSearch
          ) =>
        val userRepos = you
          .flatMap(_ => user.map(_.repos))
          .getOrElse(Set())
        SearchParams(
          q,
          page,
          sort,
          userRepos,
          topics = topics.toList,
          targetTypes = targetTypes.toList,
          scalaVersions = scalaVersions.toList,
          scalaJsVersions = scalaJsVersions.toList,
          scalaNativeVersions = scalaNativeVersions.toList,
          sbtVersions = sbtVersions.toList,
          contributingSearch = contributingSearch
        )
    }
  import ch.epfl.scala.index.data.project.ProjectForm
  import ch.epfl.scala.index.data.github.Json4s
  import org.json4s.native.Serialization.read

  val editForm: Directive1[ProjectForm] =
    formFieldSeq.tflatMap(fields =>
      formFields(
        (
          "contributorsWanted".as[Boolean] ? false,
          "defaultArtifact".?,
          "defaultStableVersion".as[Boolean] ? false,
          "strictVersions".as[Boolean] ? false,
          "deprecated".as[Boolean] ? false,
          "artifactDeprecations".as[String].*,
          "cliArtifacts".as[String].*,
          "customScalaDoc".?,
          "primaryTopic".?,
          "beginnerIssuesLabel".?,
          "beginnerIssues".?,
          "selectedBeginnerIssues".as[String].*,
          "chatroom".?,
          "contributingGuide".?,
          "codeOfConduct".?
        )
      ).tmap {
        case (
              contributorsWanted,
              defaultArtifact,
              defaultStableVersion,
              strictVersions,
              deprecated,
              artifactDeprecations,
              cliArtifacts,
              customScalaDoc,
              primaryTopic,
              beginnerIssuesLabel,
              beginnerIssues,
              selectedBeginnerIssues,
              chatroom,
              contributingGuide,
              codeOfConduct
            ) =>
          val documentationLinks = {
            val name = "documentationLinks"
            val end: Char = ']'

            fields._1
              .filter { case (key, _) => key.startsWith(name) }
              .groupBy { case (key, _) =>
                key
                  .drop("documentationLinks[".length)
                  .takeWhile(_ != end)
              }
              .values
              .map { case Vector((a, b), (_, d)) =>
                if (a.contains("label")) (b, d)
                else (d, b)
              }
              .toList
          }

          val keywords = Set[String]()

          import Json4s._
          ProjectForm(
            contributorsWanted,
            keywords,
            defaultArtifact,
            defaultStableVersion,
            strictVersions,
            deprecated,
            artifactDeprecations.toSet,
            cliArtifacts.toSet,
            customScalaDoc,
            documentationLinks,
            primaryTopic,
            beginnerIssuesLabel,
            beginnerIssues.map(read[List[GithubIssue]](_)).getOrElse(List()),
            selectedBeginnerIssues.map(read[GithubIssue](_)).toList,
            chatroom.map(Url),
            contributingGuide.map(Url),
            codeOfConduct.map(Url)
          )
      }
    )
}
