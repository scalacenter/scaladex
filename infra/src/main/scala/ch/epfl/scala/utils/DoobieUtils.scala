package ch.epfl.scala.utils

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.util.Try

import cats.effect.ContextShift
import cats.effect.IO
import cats.effect._
import ch.epfl.scala.index.model.License
import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.misc.GithubContributor
import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubIssue
import ch.epfl.scala.index.model.misc.GithubStatus
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.model.release.Resolver
import ch.epfl.scala.index.newModel.Artifact
import ch.epfl.scala.index.newModel.ArtifactDependency
import ch.epfl.scala.index.newModel.Project
import ch.epfl.scala.index.newModel.Project.DocumentationLink
import ch.epfl.scala.index.newModel.Project.Organization
import ch.epfl.scala.index.newModel.Project.Repository
import ch.epfl.scala.services.storage.sql.DatabaseConfig
import ch.epfl.scala.utils.Codecs._
import ch.epfl.scala.utils.ScalaExtensions._
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.util.Read
import doobie.util.Write
import doobie.util.fragment.Fragment
import io.circe._
import org.flywaydb.core.Flyway

object DoobieUtils {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)
  def flyway(conf: DatabaseConfig): Flyway = {
    val datasource = getHikariDataSource(conf)
    Flyway
      .configure()
      .dataSource(datasource)
      .locations("classpath:migrations")
      .load()
  }

  def transactor(conf: DatabaseConfig): Resource[IO, HikariTransactor[IO]] = {
    val datasource = getHikariDataSource(conf)
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](32) // our connect EC
      be <- Blocker[IO] // our blocking EC
    } yield Transactor.fromDataSource[IO](datasource, ce, be)
  }
  private def getHikariDataSource(conf: DatabaseConfig): HikariDataSource = {
    val config: HikariConfig = new HikariConfig()
    conf match {
      case c: DatabaseConfig.H2 =>
        config.setDriverClassName(c.driver)
        config.setJdbcUrl(c.url)
      case c: DatabaseConfig.PostgreSQL =>
        config.setDriverClassName(c.driver)
        config.setJdbcUrl(c.url)
        config.setUsername(c.user)
        config.setPassword(c.pass.decode)
    }
    new HikariDataSource(config)
  }

  def insertOrUpdateRequest[T: Write](
      table: String,
      fields: Seq[String],
      onConflictFields: Seq[String],
      action: String = "NOTHING"
  ): Update[T] = {
    val insert = insertRequest(table, fields).sql
    val onConflictFieldsStr = onConflictFields.mkString(",")
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

  object Fragments {
    val empty: Fragment = fr0""
    val space: Fragment = fr0" "

    def buildInsert(
        table: Fragment,
        fields: Fragment,
        values: Fragment
    ): Fragment =
      fr"INSERT INTO" ++ table ++ fr0" (" ++ fields ++ fr0") VALUES (" ++ values ++ fr0")"

    def buildInsertOrUpdate(
        table: Fragment,
        fields: Fragment,
        values: Fragment,
        onConflictFields: Fragment,
        action: Fragment
    ): Fragment =
      buildInsert(table, fields, values) ++
        fr" ON CONFLICT" ++ fr0"(" ++ onConflictFields ++ fr")" ++ fr"DO" ++ action

    def buildUpdate(
        table: Fragment,
        fields: Fragment,
        where: Fragment
    ): Fragment =
      fr"UPDATE" ++ table ++ fr" SET" ++ fields ++ space ++ where

    def buildSelect(table: Fragment, fields: Fragment): Fragment =
      fr"SELECT" ++ fields ++ fr" FROM" ++ table

    def buildSelect(
        table: Fragment,
        fields: Fragment,
        conditions: Fragment
    ): Fragment =
      buildSelect(table, fields) ++ space ++ conditions

    def whereRef(ref: Project.Reference): Fragment =
      fr0"WHERE organization=${ref.organization} AND repository=${ref.repository}"
  }

  object Mappings {
    implicit val contributorMeta: Meta[List[GithubContributor]] =
      Meta[String].timap(fromJson[List[GithubContributor]](_).get)(toJson(_))
    implicit val githubIssuesMeta: Meta[List[GithubIssue]] =
      Meta[String].timap(fromJson[List[GithubIssue]](_).get)(toJson(_))
    implicit val documentationLinksMeta: Meta[List[DocumentationLink]] =
      Meta[String].timap(fromJson[List[DocumentationLink]](_).get)(toJson(_))
    implicit val topicsMeta: Meta[Set[String]] =
      Meta[String].timap(_.split(",").filter(_.nonEmpty).toSet)(
        _.mkString(",")
      )
    implicit val artifactNamesMeta: Meta[Set[Artifact.Name]] =
      Meta[String].timap(_.split(",").filter(_.nonEmpty).map(Artifact.Name.apply).toSet)(_.mkString(","))
    implicit val semanticVersionMeta: Meta[SemanticVersion] =
      Meta[String].timap(SemanticVersion.tryParse(_).get)(_.toString)
    implicit val platformMeta: Meta[Platform] =
      Meta[String].timap { x =>
        Platform
          .parse(x)
          .toTry(new Exception(s"Failed to parse $x as Platform"))
          .get
      }(_.encode)
    implicit val licensesMeta: Meta[Set[License]] =
      Meta[String].timap(fromJson[List[License]](_).get.toSet)(toJson(_))
    implicit val resolverMeta: Meta[Resolver] =
      Meta[String].timap(Resolver.from(_).get)(_.name)

    implicit val instantMeta: doobie.Meta[Instant] = doobie.postgres.implicits.JavaTimeInstantMeta
    implicit val projectReferenceRead: doobie.Read[Project.Reference] =
      Read[(Organization, Repository)].map { case (org, repo) => Project.Reference(org, repo) }
    implicit val projectReferenceWrite: doobie.Write[Project.Reference] =
      Write[(String, String)].contramap(p => (p.organization.value, p.repository.value))

    implicit val dependencyWriter: Write[ArtifactDependency] =
      Write[(String, String, String, String, String, String, String)]
        .contramap { d =>
          (
            d.source.groupId,
            d.source.artifactId,
            d.source.version,
            d.target.groupId,
            d.target.artifactId,
            d.target.version,
            d.scope
          )
        }

    implicit val githubStatusRead: Read[GithubStatus] =
      Read[(String, Instant, Option[Organization], Option[Repository], Option[Int], Option[String])]
        .map {
          case ("Unknown", update, _, _, _, _)  => GithubStatus.Unknown(update)
          case ("Ok", update, _, _, _, _)       => GithubStatus.Ok(update)
          case ("NotFound", update, _, _, _, _) => GithubStatus.NotFound(update)
          case ("Moved", update, Some(organization), Some(repository), _, _) =>
            GithubStatus.Moved(update, organization, repository)
          case ("Failed", update, _, _, Some(errorCode), Some(errorMessage)) =>
            GithubStatus.Failed(update, errorCode, errorMessage)
          case invalid =>
            throw new Exception(s"Cannot read github status from database: $invalid")
        }
    implicit val githubStatusWrite: Write[GithubStatus] =
      Write[(String, Instant, Option[Organization], Option[Repository], Option[Int], Option[String])]
        .contramap {
          case GithubStatus.Unknown(update)  => ("Unknown", update, None, None, None, None)
          case GithubStatus.Ok(update)       => ("Ok", update, None, None, None, None)
          case GithubStatus.NotFound(update) => ("NotFound", update, None, None, None, None)
          case GithubStatus.Moved(update, organization, repository) =>
            ("Moved", update, Some(organization), Some(repository), None, None)
          case GithubStatus.Failed(update, errorCode, errorMessage) =>
            ("Failed", update, None, None, Some(errorCode), Some(errorMessage))
        }

    implicit val projectReader: Read[Project] =
      Read[(Organization, Repository, Option[Instant], GithubStatus, Option[GithubInfo], Option[Project.DataForm])]
        .map {
          case (organization, repository, creationDate, githubStatus, githubInfo, dataForm) =>
            Project(
              organization = organization,
              repository = repository,
              githubStatus = githubStatus,
              githubInfo = githubInfo,
              creationDate = creationDate,
              dataForm = dataForm.getOrElse(Project.DataForm.default)
            )
        }

    private def toJson[A](v: A)(implicit e: Encoder[A]): String =
      e.apply(v).noSpaces

    private def fromJson[A](s: String)(implicit d: Decoder[A]): Try[A] =
      parser.parse(s).flatMap(d.decodeJson).toTry
  }

}
