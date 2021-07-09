package ch.epfl.scala.services.storage.sql

import ch.epfl.scala.utils.Secret

import scala.util.{Failure, Success, Try}

sealed trait DbConf extends Product with Serializable

object DbConf {

  private val h2Regex = "(jdbc:h2:mem:.*)".r
  private val postgreSQLRegex =
    "(?:jdbc:)?postgres(?:ql)?://([^:]+):([^@]+)@([a-z0-9-.:/]+)".r

  def from(url: String): Try[DbConf] = url match {
    case h2Regex(value) => Success(H2(value))
    case postgreSQLRegex(login, pass, url) =>
      Success(PostgreSQL(s"jdbc:postgresql://$url", login, Secret(pass)))
    case _ => Failure(new Exception(s"Unknown db url: $url"))
  }

  final case class H2(url: String) extends DbConf {
    val driver = "org.h2.Driver"
  }

  final case class PostgreSQL(url: String, user: String, pass: Secret)
      extends DbConf {
    val driver = "org.postgresql.Driver"
  }
}
