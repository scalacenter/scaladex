package scaladex.infra.config

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import scaladex.core.util.Secret

final case class PostgreSQLConfig(url: String, user: String, pass: Secret):
  val driver = "org.postgresql.Driver"

object PostgreSQLConfig:
  private val postgreSQLRegex =
    "(?:jdbc:)?postgres(?:ql)?://([^:]+):([^@]+)@([a-z0-9-.:/]+)".r

  def load(): Try[PostgreSQLConfig] =
    val config = ConfigFactory.load()
    from(config)

  def from(config: Config): Try[PostgreSQLConfig] =
    from(config.getString("scaladex.database.url"))

  private def from(url: String): Try[PostgreSQLConfig] = url match
    case postgreSQLRegex(login, pass, url) =>
      Success(PostgreSQLConfig(s"jdbc:postgresql://$url", login, Secret(pass)))
    case _ => Failure(new Exception(s"Unknown database url: $url"))
end PostgreSQLConfig
