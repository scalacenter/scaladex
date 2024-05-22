package scaladex.infra.sql

import java.time.Instant
import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.util.Try

import cats.effect.ContextShift
import cats.effect.IO
import cats.effect._
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import doobie._
import doobie.hikari.HikariTransactor
import io.circe._
import org.flywaydb.core.Flyway
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Category
import scaladex.core.model.DocumentationPattern
import scaladex.core.model.GithubCommitActivity
import scaladex.core.model.GithubContributor
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubIssue
import scaladex.core.model.GithubStatus
import scaladex.core.model.Language
import scaladex.core.model.License
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.Project._
import scaladex.core.model.Resolver
import scaladex.core.model.SemanticVersion
import scaladex.core.model.UserInfo
import scaladex.core.model.UserState
import scaladex.core.util.Secret
import scaladex.infra.Codecs._
import scaladex.infra.config.PostgreSQLConfig

object DoobieUtils {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  def flyway(conf: PostgreSQLConfig): Flyway = {
    val datasource = getHikariDataSource(conf)
    flyway(datasource)
  }
  def flyway(datasource: HikariDataSource): Flyway =
    Flyway
      .configure()
      .dataSource(datasource)
      .locations("migrations", "scaladex/infra/migrations")
      .load()

  def transactor(datasource: HikariDataSource): Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](32) // our connect EC
      be <- Blocker[IO] // our blocking EC
    } yield Transactor.fromDataSource[IO](datasource, ce, be)

  def getHikariDataSource(conf: PostgreSQLConfig): HikariDataSource = {
    val config: HikariConfig = new HikariConfig()
    config.setDriverClassName(conf.driver)
    config.setJdbcUrl(conf.url)
    config.setUsername(conf.user)
    config.setPassword(conf.pass.decode)
    new HikariDataSource(config)
  }

  def insertOrUpdateRequest[T: Write](
      table: String,
      insertFields: Seq[String],
      onConflictFields: Seq[String],
      updateFields: Seq[String] = Seq.empty
  ): Update[T] = {
    val insert = insertRequest(table, insertFields).sql
    val onConflictFieldsStr = onConflictFields.mkString(",")
    val action = if (updateFields.nonEmpty) updateFields.mkString(" UPDATE SET ", " = ?, ", " = ?") else "NOTHING"
    Update(s"$insert ON CONFLICT ($onConflictFieldsStr) DO $action")
  }

  def insertRequest[T: Write](table: String, fields: Seq[String]): Update[T] = {
    val fieldsStr = fields.mkString(", ")
    val valuesStr = fields.map(_ => "?").mkString(", ")
    Update(s"INSERT INTO $table ($fieldsStr) VALUES ($valuesStr)")
  }

  def updateRequest[T: Write](table: String, fields: Seq[String], keys: Seq[String]): Update[T] = {
    val fieldsStr = fields.map(f => s"$f=?").mkString(", ")
    val keysStr = keys.map(k => s"$k=?").mkString(" AND ")
    Update(s"UPDATE $table SET $fieldsStr WHERE $keysStr")
  }

  def selectRequest[A: Read](table: String, fields: Seq[String]): Query0[A] = {
    val fieldsStr = fields.mkString(", ")
    Query0(s"SELECT $fieldsStr FROM $table")
  }

  def selectRequest[A: Write, B: Read](table: String, fields: Seq[String], keys: Seq[String]): Query[A, B] = {
    val fieldsStr = fields.mkString(", ")
    val keysStr = keys.map(k => s"$k=?").mkString(" AND ")
    Query(s"SELECT $fieldsStr FROM $table WHERE $keysStr")
  }

  def selectRequest[A: Read](
      table: String,
      fields: Seq[String],
      where: Seq[String] = Seq.empty,
      groupBy: Seq[String] = Seq.empty,
      orderBy: Option[String] = None,
      limit: Option[Long] = None
  ): Query0[A] = {
    val fieldsStr = fields.mkString(", ")
    val whereStr = if (where.nonEmpty) where.mkString(" WHERE ", " AND ", "") else ""
    val groupByStr = if (groupBy.nonEmpty) groupBy.mkString(" GROUP BY ", ", ", "") else ""
    val orderByStr = orderBy.map(o => s" ORDER BY $o").getOrElse("")
    val limitStr = limit.map(l => s" LIMIT $l").getOrElse("")
    Query0(s"SELECT $fieldsStr FROM $table" + whereStr + groupByStr + orderByStr + limitStr)
  }

  def selectRequest1[A: Write, B: Read](
      table: String,
      fields: Seq[String],
      where: Seq[String] = Seq.empty,
      groupBy: Seq[String] = Seq.empty,
      orderBy: Option[String] = None,
      limit: Option[Long] = None
  ): Query[A, B] = {
    val fieldsStr = fields.mkString(", ")
    val whereStr = if (where.nonEmpty) where.mkString(" WHERE ", " AND ", "") else ""
    val groupByStr = if (groupBy.nonEmpty) groupBy.mkString(" GROUP BY ", ", ", "") else ""
    val orderByStr = orderBy.map(o => s" ORDER BY $o").getOrElse("")
    val limitStr = limit.map(l => s" LIMIT $l").getOrElse("")
    Query(s"SELECT $fieldsStr FROM $table" + whereStr + groupByStr + orderByStr + limitStr)
  }

  def deleteRequest[T: Write](table: String, where: Seq[String]): Update[T] = {
    val whereK = where.map(k => s"$k=?").mkString(" AND ")
    Update(s"DELETE FROM $table WHERE $whereK")
  }

  object Mappings {
    implicit val contributorMeta: Meta[Seq[GithubContributor]] =
      Meta[String].timap(fromJson[Seq[GithubContributor]](_).get)(toJson(_))
    implicit val githubIssuesMeta: Meta[Seq[GithubIssue]] =
      Meta[String].timap(fromJson[Seq[GithubIssue]](_).get)(toJson(_))
    implicit val documentationPattern: Meta[Seq[DocumentationPattern]] =
      Meta[String].timap(fromJson[Seq[DocumentationPattern]](_).get)(toJson(_))
    implicit val githubCommitActivityMeta: Meta[Seq[GithubCommitActivity]] =
      Meta[String].timap(fromJson[Seq[GithubCommitActivity]](_).get)(toJson(_))
    implicit val topicsMeta: Meta[Set[String]] =
      Meta[String].timap(_.split(",").filter(_.nonEmpty).toSet)(
        _.mkString(",")
      )
    implicit val artifactNamesMeta: Meta[Set[Artifact.Name]] =
      Meta[String].timap(_.split(",").filter(_.nonEmpty).map(Artifact.Name.apply).toSet)(_.mkString(","))
    implicit val semanticVersionMeta: Meta[SemanticVersion] =
      Meta[String].timap(SemanticVersion.parse(_).get)(_.encode)
    implicit val artifactIdMeta: Meta[Artifact.ArtifactId] =
      Meta[String].timap(Artifact.ArtifactId.parse(_).get)(_.value)
    implicit val binaryVersionMeta: Meta[BinaryVersion] =
      Meta[String].timap { x =>
        BinaryVersion
          .parse(x)
          .getOrElse(throw new Exception(s"Failed to parse $x as BinaryVersion"))
      }(_.encode)
    implicit val platformMeta: Meta[Platform] =
      Meta[String].timap { x =>
        Platform
          .fromLabel(x)
          .getOrElse(throw new Exception(s"Failed to parse $x as Platform"))
      }(_.label)
    implicit val languageVersionMeta: Meta[Language] =
      Meta[String].timap { x =>
        Language
          .fromLabel(x)
          .getOrElse(throw new Exception(s"Failed to parse $x as Language"))
      }(_.label)
    implicit val scopeMeta: Meta[ArtifactDependency.Scope] =
      Meta[String].timap(ArtifactDependency.Scope.apply)(_.value)
    implicit val licenseMeta: Meta[License] =
      Meta[String].timap { x =>
        License
          .get(x)
          .getOrElse(throw new Exception(s"Failed to parse $x as License"))
      }(_.shortName)
    implicit val licensesMeta: Meta[Set[License]] =
      Meta[String].timap(fromJson[Seq[License]](_).get.toSet)(toJson(_))
    implicit val resolverMeta: Meta[Resolver] =
      Meta[String].timap(Resolver.from(_).get)(_.name)
    implicit val instantMeta: Meta[Instant] = doobie.postgres.implicits.JavaTimeInstantMeta

    implicit val secretMeta: Meta[Secret] = Meta[String].imap[Secret](Secret.apply)(_.decode)
    implicit val uuidMeta: Meta[UUID] = Meta[String].imap[UUID](UUID.fromString)(_.toString)
    implicit val projectReferenceMeta: Meta[Set[Project.Reference]] =
      Meta[String].timap(_.split(",").filter(_.nonEmpty).map(Project.Reference.from).toSet)(_.mkString(","))
    implicit val projectOrganizationMeta: Meta[Set[Project.Organization]] =
      Meta[String].timap(_.split(",").filter(_.nonEmpty).map(Project.Organization.apply).toSet)(_.mkString(","))

    implicit val categoryMeta: Meta[Category] = Meta[String].timap(Category.byLabel)(_.label)

    implicit val projectReferenceRead: Read[Project.Reference] =
      Read[(Organization, Repository)].map { case (org, repo) => Project.Reference(org, repo) }
    implicit val projectReferenceWrite: Write[Project.Reference] =
      Write[(String, String)].contramap(p => (p.organization.value, p.repository.value))

    implicit val githubStatusRead: Read[GithubStatus] =
      Read[(String, Instant, Option[Organization], Option[Repository], Option[Int], Option[String])]
        .map {
          case ("Unknown", updateDate, _, _, _, _)  => GithubStatus.Unknown(updateDate)
          case ("Ok", updateDate, _, _, _, _)       => GithubStatus.Ok(updateDate)
          case ("NotFound", updateDate, _, _, _, _) => GithubStatus.NotFound(updateDate)
          case ("Moved", updateDate, Some(organization), Some(repository), _, _) =>
            GithubStatus.Moved(updateDate, Project.Reference(organization, repository))
          case ("Failed", updateDate, _, _, Some(errorCode), Some(errorMessage)) =>
            GithubStatus.Failed(updateDate, errorCode, errorMessage)
          case invalid =>
            throw new Exception(s"Cannot read github status from database: $invalid")
        }
    implicit val githubStatusWrite: Write[GithubStatus] =
      Write[(String, Instant, Option[Organization], Option[Repository], Option[Int], Option[String])]
        .contramap {
          case GithubStatus.Unknown(updateDate)  => ("Unknown", updateDate, None, None, None, None)
          case GithubStatus.Ok(updateDate)       => ("Ok", updateDate, None, None, None, None)
          case GithubStatus.NotFound(updateDate) => ("NotFound", updateDate, None, None, None, None)
          case GithubStatus.Moved(updateDate, projectRef) =>
            ("Moved", updateDate, Some(projectRef.organization), Some(projectRef.repository), None, None)
          case GithubStatus.Failed(updateDate, errorCode, errorMessage) =>
            ("Failed", updateDate, None, None, Some(errorCode), Some(errorMessage))
        }

    implicit val projectReader: Read[Project] =
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

    implicit val writeUserInfo: Write[UserInfo] =
      Write[(String, Option[String], String, Secret)].contramap {
        case UserInfo(login, name, avatarUrl, token) => (login, name, avatarUrl, token)
      }
    implicit val readUserInfo: Read[UserInfo] =
      Read[(String, Option[String], String, Secret)].map {
        case (login, name, avatarUrl, token) => UserInfo(login, name, avatarUrl, token)
      }
    implicit val writeUserState: Write[UserState] =
      Write[(Set[Project.Reference], Set[Project.Organization], UserInfo)].contramap {
        case UserState(repos, orgs, info) => (repos, orgs, info)
      }
    implicit val readUserState: Read[UserState] =
      Read[(Set[Project.Reference], Set[Project.Organization], UserInfo)].map {
        case (repos, orgs, info) => UserState(repos, orgs, info)
      }

    private def toJson[A](v: A)(implicit e: Encoder[A]): String =
      e.apply(v).noSpaces

    private def fromJson[A](s: String)(implicit d: Decoder[A]): Try[A] =
      parser.parse(s).flatMap(d.decodeJson).toTry
  }

}
