package scaladex.infra.config

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import scaladex.core.util.Secret

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

final case class PostgreSQLConfig(
    url: String,
    user: String,
    pass: Secret,
    poolSize: Int,
    statementTimeout: FiniteDuration
):
  val driver = "org.postgresql.Driver"

object PostgreSQLConfig:
  private val postgreSQLRegex =
    "(?:jdbc:)?postgres(?:ql)?://([^:]+):([^@]+)@([a-z0-9-.:/]+)".r

  def load(): Try[PostgreSQLConfig] =
    val config = ConfigFactory.load()
    from(config)

  def from(config: Config): Try[PostgreSQLConfig] =
    val statementTimeout =
      FiniteDuration(config.getDuration("scaladex.database.statement-timeout").toNanos, NANOSECONDS)
    from(config.getString("scaladex.database.url"), config.getInt("scaladex.database.pool-size"), statementTimeout)

  private def from(url: String, poolSize: Int, statementTimeout: FiniteDuration): Try[PostgreSQLConfig] = url match
    case postgreSQLRegex(login, pass, url) =>
      Success(PostgreSQLConfig(s"jdbc:postgresql://$url", login, Secret(pass), poolSize, statementTimeout))
    case _ => Failure(new Exception(s"Unknown database url: $url"))
end PostgreSQLConfig
