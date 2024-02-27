import java.nio.file.Path
import java.sql.DriverManager

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.util.Try

import org.testcontainers.containers.BindMode
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.containers.wait.strategy.WaitAllStrategy
import org.testcontainers.dockerclient.DockerClientProviderStrategy
import org.testcontainers.utility.DockerImageName
import sbt._

object Postgres extends AutoPlugin {
  private val containers: mutable.Map[Path, PostgreSQLContainer[Nothing]] = TrieMap.empty

  object autoImport {
    val startPostgres = taskKey[Int]("Connect to Postgres or start a Postgres container")
  }

  import autoImport._

  def settings(config: Configuration, defaultPort: Int, database: String): Seq[Setting[_]] = Seq(
    config / startPostgres := {
      import sbt.util.CacheImplicits._
      val dataFolder = Keys.baseDirectory.value / s".postgresql-${config.name}"
      val streams = Keys.streams.value
      val logger = streams.log

      if (canConnect(defaultPort, database)) {
        logger.info(s"Postgres is available on port $defaultPort")
        defaultPort
      } else {
        // we cache the container to reuse it after a reload
        val store = streams.cacheStoreFactory.make("container")
        val tracker = util.Tracked.lastOutput[Unit, (String, Int)](store) {
          case (_, None) =>
            startContainer(dataFolder, database, logger)
          case (_, Some((containerId, port))) =>
            if (canConnect(port, database)) {
              logger.info(s"Postgres container already started on port $port")
              (containerId, port)
            } else {
              Docker.kill(containerId)
              startContainer(dataFolder, database, logger)
            }
        }
        tracker(())._2
      }
    },
    Keys.clean := {
      Keys.clean.value
      val dataFolder = Keys.baseDirectory.value / s".postgresql-${config.name}"
      containers.get(dataFolder.toPath).foreach(_.close())
      containers.remove(dataFolder.toPath)
    }
  )

  private def startContainer(dataFolder: File, database: String, logger: Logger): (String, Int) = {
    if (!dataFolder.exists) IO.createDirectory(dataFolder)
    IO.setPermissions(dataFolder, "rwxrwxrwx")

    CurrentThread.setContextClassLoader[DockerClientProviderStrategy]

    val dockerImage = DockerImageName.parse("postgres").withTag("13.3")
    val container = new PostgreSQLContainer(dockerImage)

    // change the wait strategy because of https://github.com/testcontainers/testcontainers-java/issues/455
    // and https://github.com/testcontainers/testcontainers-java/issues/3372
    val hostPort = new HostPortWaitStrategy()
    val logMessage = new LogMessageWaitStrategy().withRegEx(".*database system is ready to accept connections.*")
    val portAndMessage = new WaitAllStrategy().withStrategy(hostPort).withStrategy(logMessage)
    container.waitingFor(portAndMessage)

    container.withDatabaseName(database)
    container.withUsername("user")
    container.withPassword("password")
    container.withEnv("PGDATA", "/usr/share/postgres/data")
    container.withFileSystemBind(
      dataFolder.toString,
      "/usr/share/postgres",
      BindMode.READ_WRITE
    )

    val port =
      try {
        container.start()
        container.getFirstMappedPort()
      } catch {
        case e: Throwable =>
          container.close()
          throw e
      }
    logger.info(s"Postgres container started on port $port")
    containers(dataFolder.toPath) = container
    (container.getContainerId(), port)
  }

  private def canConnect(port: Int, database: String): Boolean = {
    // `CurrentThread.setContextClassLoader[org.postgresql.Driver]` should work but it does not
    CurrentThread.setContextClassLoader("org.postgresql.Driver")
    try {
      val connection = DriverManager.getConnection(
        s"jdbc:postgresql://localhost:$port/$database",
        "user",
        "password"
      )
      connection.close()
      true
    } catch {
      case _: Throwable => false
    }
  }
}
