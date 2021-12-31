package scaladex.infra.storage.sql

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import scaladex.core.util.Secret

sealed trait DatabaseConfig extends Product with Serializable

object DatabaseConfig {
  private val h2Regex = "(jdbc:h2:*:.*)".r
  private val postgreSQLRegex =
    "(?:jdbc:)?postgres(?:ql)?://([^:]+):([^@]+)@([a-z0-9-.:/]+)".r

  def load(): Try[DatabaseConfig] = {
    val config = ConfigFactory.load()
    from(config)
  }

  def from(config: Config): Try[DatabaseConfig] =
    from(config.getString("scaladex.database.url"))

  private def from(url: String): Try[DatabaseConfig] = url match {
    case h2Regex(value) => Success(H2(value))
    case postgreSQLRegex(login, pass, url) =>
      Success(PostgreSQL(s"jdbc:postgresql://$url", login, Secret(pass)))
    case _ => Failure(new Exception(s"Unknown database url: $url"))
  }

  final case class H2(url: String) extends DatabaseConfig {
    val driver = "org.h2.Driver"
  }

  final case class PostgreSQL(url: String, user: String, pass: Secret) extends DatabaseConfig {
    val driver = "org.postgresql.Driver"
  }
}
