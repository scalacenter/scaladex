package scaladex.infra.sql

import java.time.Instant
import java.util.UUID

import scala.util.Try

import scaladex.core.model.*
import scaladex.core.model.Project.*
import scaladex.core.util.Secret
import scaladex.infra.Codecs.given

import doobie.*
import doobie.postgres.Instances
import doobie.postgres.JavaTimeInstances
import io.circe.*

object DoobieMappings extends Instances with JavaTimeInstances:
  given given_Meta_Seq_GithubContributor: Meta[Seq[GithubContributor]] =
    Meta[String].timap(fromJson[Seq[GithubContributor]](_).get)(toJson(_))
  given given_Meta_Seq_GithubIssue: Meta[Seq[GithubIssue]] =
    Meta[String].timap(fromJson[Seq[GithubIssue]](_).get)(toJson(_))
  given given_Meta_Seq_DocumentationPattern: Meta[Seq[DocumentationPattern]] =
    Meta[String].timap(fromJson[Seq[DocumentationPattern]](_).get)(toJson(_))
  given given_Meta_Seq_GithubCommitActivity: Meta[Seq[GithubCommitActivity]] =
    Meta[String].timap(fromJson[Seq[GithubCommitActivity]](_).get)(toJson(_))
  given given_Meta_Set_String: Meta[Set[String]] =
    Meta[String].timap(_.split(",").filter(_.nonEmpty).toSet)(_.mkString(","))
  given given_Meta_Set_Artifact_Name: Meta[Set[Artifact.Name]] =
    Meta[String].timap(_.split(",").filter(_.nonEmpty).map(Artifact.Name.apply).toSet)(_.mkString(","))
  given Meta[Version] = Meta[String].timap(Version(_))(_.value)
  given Meta[Artifact.GroupId] = Meta[String].timap(Artifact.GroupId(_))(_.value)
  given Meta[Artifact.ArtifactId] = Meta[String].timap(Artifact.ArtifactId(_))(_.value)
  given Meta[Artifact.Name] = Meta[String].timap(Artifact.Name(_))(_.value)
  given Meta[BinaryVersion] =
    Meta[String].timap { x =>
      BinaryVersion.parse(x).getOrElse(throw new Exception(s"Failed to parse $x as BinaryVersion"))
    }(_.value)
  given Meta[Platform] =
    Meta[String]
      .timap(x => Platform.parse(x).getOrElse(throw new Exception(s"Failed to parse $x as Platform")))(_.value)
  given Meta[Language] =
    Meta[String]
      .timap(x => Language.parse(x).getOrElse(throw new Exception(s"Failed to parse $x as Language")))(_.value)
  given Meta[ArtifactDependency.Scope] = Meta[String].timap(ArtifactDependency.Scope.apply)(_.value)
  given Meta[License] =
    Meta[String]
      .timap(x => License.get(x).getOrElse(throw new Exception(s"Failed to parse $x as License")))(_.shortName)
  given given_Meta_Set_License: Meta[Set[License]] = Meta[String].timap(fromJson[Seq[License]](_).get.toSet)(toJson(_))
  given Meta[Resolver] = Meta[String].timap(Resolver.from(_).get)(_.name)
  given Meta[Seq[Contributor]] = Meta[String].timap(fromJson[Seq[Contributor]](_).get)(toJson(_))
  given Meta[Url] = Meta[String].timap(Url(_))(_.target)

  given Meta[Secret] = Meta[String].imap[Secret](Secret.apply)(_.decode)
  given Meta[UUID] = Meta[String].imap[UUID](UUID.fromString)(_.toString)

  given Meta[Project.Repository] = Meta[String].timap(Project.Repository.apply)(_.value)
  given Meta[Project.Organization] =
    Meta[String].timap(Project.Organization.apply)(_.value)
  given given_Meta_Set_Project_Reference: Meta[Set[Project.Reference]] =
    Meta[String].timap(_.split(",").filter(_.nonEmpty).map(Project.Reference.unsafe).toSet)(_.mkString(","))
  given given_Meta_Set_Project_Organization: Meta[Set[Project.Organization]] =
    Meta[String].timap(_.split(",").filter(_.nonEmpty).map(Project.Organization.apply).toSet)(_.mkString(","))
  given Meta[Category] = Meta[String].timap(Category.byLabel)(_.label)

  given Read[Project.Reference] =
    Read[(Organization, Repository)].map { case (org, repo) => Project.Reference(org, repo) }
  given Write[Project.Reference] =
    Write[(String, String)].contramap(p => (p.organization.value, p.repository.value))

  given Read[GithubStatus] =
    Read[(String, Instant, Option[Organization], Option[Repository], Option[Int], Option[String])]
      .map {
        case ("Unknown", updateDate, _, _, _, _) => GithubStatus.Unknown(updateDate)
        case ("Ok", updateDate, _, _, _, _) => GithubStatus.Ok(updateDate)
        case ("NotFound", updateDate, _, _, _, _) => GithubStatus.NotFound(updateDate)
        case ("Moved", updateDate, Some(organization), Some(repository), _, _) =>
          GithubStatus.Moved(updateDate, Project.Reference(organization, repository))
        case ("Failed", updateDate, _, _, Some(errorCode), Some(errorMessage)) =>
          GithubStatus.Failed(updateDate, errorCode, errorMessage)
        case invalid =>
          throw new Exception(s"Cannot read github status from database: $invalid")
      }
  given Write[GithubStatus] =
    Write[(String, Instant, Option[Organization], Option[Repository], Option[Int], Option[String])]
      .contramap {
        case GithubStatus.Unknown(updateDate) => ("Unknown", updateDate, None, None, None, None)
        case GithubStatus.Ok(updateDate) => ("Ok", updateDate, None, None, None, None)
        case GithubStatus.NotFound(updateDate) => ("NotFound", updateDate, None, None, None, None)
        case GithubStatus.Moved(updateDate, projectRef) =>
          ("Moved", updateDate, Some(projectRef.organization), Some(projectRef.repository), None, None)
        case GithubStatus.Failed(updateDate, errorCode, errorMessage) =>
          ("Failed", updateDate, None, None, Some(errorCode), Some(errorMessage))
      }

  given Read[Project] =
    Read[(Organization, Repository, Option[Instant], GithubStatus, Option[GithubInfo], Option[Project.Settings])]
      .map {
        case (organization, repository, creationDate, githubStatus, githubInfo, settings) =>
          Project(
            organization = organization,
            repository = repository,
            githubStatus = githubStatus,
            githubInfo = githubInfo,
            creationDate = creationDate,
            settings = settings.getOrElse(Project.Settings.empty)
          )
      }

  given Write[UserInfo] =
    Write[(String, Option[String], String, Secret)].contramap {
      case UserInfo(login, name, avatarUrl, token) => (login, name, avatarUrl, token)
    }
  given Read[UserInfo] =
    Read[(String, Option[String], String, Secret)].map {
      case (login, name, avatarUrl, token) => UserInfo(login, name, avatarUrl, token)
    }
  given Write[UserState] =
    Write[(Set[Project.Reference], Set[Project.Organization], UserInfo)].contramap {
      case UserState(repos, orgs, info) => (repos, orgs, info)
    }
  given Read[UserState] =
    Read[(Set[Project.Reference], Set[Project.Organization], UserInfo)].map {
      case (repos, orgs, info) => UserState(repos, orgs, info)
    }
  // not strictly needed, but keeping it to speed compilation up
  given Read[Artifact] = Read.given_Read_P

  private def toJson[A](v: A)(using Encoder[A]): String =
    Encoder[A].apply(v).noSpaces

  private def fromJson[A](s: String)(using Decoder[A]): Try[A] =
    parser.parse(s).flatMap(Decoder[A].decodeJson).toTry
end DoobieMappings
