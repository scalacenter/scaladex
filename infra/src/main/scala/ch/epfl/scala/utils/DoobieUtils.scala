package ch.epfl.scala.utils

import cats.effect.{ContextShift, IO}
import ch.epfl.scala.index.model.misc.{GithubContributor, GithubIssue, Url}
import ch.epfl.scala.index.newModel.NewProject.DocumentationLink
import ch.epfl.scala.services.storage.sql.DbConf
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie._
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.meta.Meta
import org.flywaydb.core.Flyway
import io.circe._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import scala.concurrent.ExecutionContext
import scala.util.Try

object DoobieUtils {
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  def create(conf: DbConf): (doobie.Transactor[IO], Flyway) = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    val xa: doobie.Transactor[IO] = conf match {
      case c: DbConf.H2 =>
        Transactor.fromDriverManager[IO](c.driver, c.url, "", "")
      case c: DbConf.PostgreSQL =>
        Transactor.fromDriverManager[IO](c.driver, c.url, c.user, c.pass.decode)
    }

    val config = new HikariConfig()
    conf match {
      case c: DbConf.H2 =>
        config.setDriverClassName(c.driver)
        config.setJdbcUrl(c.url)
      case c: DbConf.PostgreSQL =>
        config.setDriverClassName(c.driver)
        config.setJdbcUrl(c.url)
        config.setUsername(c.user)
        config.setPassword(c.pass.decode)
    }
    val flyway = Flyway
      .configure()
      .dataSource(new HikariDataSource(config))
      .locations("classpath:migrations")
      .load()

    (xa, flyway)
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

    def buildSelect(table: Fragment, fields: Fragment): Fragment =
      fr"SELECT" ++ fields ++ fr" FROM" ++ table

    def buildSelect(
        table: Fragment,
        fields: Fragment,
        where: Fragment
    ): Fragment =
      buildSelect(table, fields) ++ space ++ where
  }

  object Mappings {
    implicit val stringMeta: Meta[String] = Meta.StringMeta
    implicit val URLDecoder: Decoder[Url] = deriveDecoder[Url]
    implicit val URLEncoder: Encoder[Url] = deriveEncoder[Url]
    implicit val contributorMeta: Meta[List[GithubContributor]] = {
      implicit val contributorDecoder: Decoder[GithubContributor] =
        deriveDecoder[GithubContributor]
      implicit val contributorEncoder: Encoder[GithubContributor] =
        deriveEncoder[GithubContributor]
      stringMeta.timap(fromJson[List[GithubContributor]](_).get)(toJson(_))
    }
    implicit val githubIssuesMeta: Meta[List[GithubIssue]] = {
      implicit val githubIssueDecoder: Decoder[GithubIssue] =
        deriveDecoder[GithubIssue]
      implicit val githubIssueEncoder: Encoder[GithubIssue] =
        deriveEncoder[GithubIssue]
      stringMeta.timap(fromJson[List[GithubIssue]](_).get)(toJson(_))
    }
    implicit val documentationLinksMeta: Meta[List[DocumentationLink]] = {
      implicit val documentationDecoder: Decoder[DocumentationLink] =
        deriveDecoder[DocumentationLink]
      implicit val documentationEncoder: Encoder[DocumentationLink] =
        deriveEncoder[DocumentationLink]
      stringMeta.timap(fromJson[List[DocumentationLink]](_).get)(toJson(_))
    }
    implicit val topicsMeta: Meta[Set[String]] =
      stringMeta.timap(_.split(",").filter(_.nonEmpty).toSet)(
        _.mkString(",")
      )

    private def toJson[A](v: A)(implicit e: Encoder[A]): String =
      e.apply(v).noSpaces

    private def fromJson[A](s: String)(implicit d: Decoder[A]): Try[A] =
      parser.parse(s).flatMap(d.decodeJson).toTry
  }

}
