import sbt._

import org.testcontainers.dockerclient.DockerClientProviderStrategy
import org.testcontainers.utility.DockerImageName
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy

import java.sql.DriverManager
import scala.util.Try

object Postgres extends AutoPlugin {
  object autoImport {
    val postgresDefaultPort = settingKey[Int]("Default port of postgres")
    val postgresFolder =
      settingKey[File]("Folder where postgres data are stored")
    val postgresDatabase = settingKey[String]("Name of the postgres database")
    val startPostgres = taskKey[Int](
      "Chek that postgres has already started or else start a container"
    )
  }

  import autoImport._

  def settings(defaultPort: Int, database: String): Seq[Setting[_]] = Seq(
    postgresDefaultPort := defaultPort,
    postgresFolder := {
      val c = Keys.configuration.?.value
      val suffix = c.map(c => s"-${c.name}").getOrElse("")
      Keys.baseDirectory.value / s".postgresql$suffix"
    },
    postgresDatabase := database,
    startPostgres := {
      import sbt.util.CacheImplicits._
      val dataFolder = postgresFolder.value
      val defaultPort = postgresDefaultPort.value
      val database = postgresDatabase.value
      val streams = Keys.streams.value
      val store = streams.cacheStoreFactory.make("last")
      val logger = streams.log
      val tracker = util.Tracked.lastOutput[Unit, Int](store) {
        case (_, None) =>
          checkOrStart(dataFolder, defaultPort, database, logger)
        case (_, Some(previousPort)) =>
          checkOrStart(dataFolder, previousPort, database, logger)
      }
      tracker(())
    }
  )

  private def checkOrStart(
      dataFolder: File,
      previousPort: Int,
      database: String,
      logger: Logger
  ): Int =
    if (alreadyStarted(previousPort, database, logger)) {
      logger.info(s"Postgres has already started on port $previousPort")
      previousPort
    } else {
      logger.info("Trying to start postgres container")
      val port = start(dataFolder, database)
      logger.info(s"Postgres container successfully started with port $port")
      port
    }

  private def start(dataFolder: File, database: String): Int = {
    if (!dataFolder.exists) IO.createDirectory(dataFolder)
    IO.setPermissions(dataFolder, "rwxrwxrwx")

    CurrentThread.setContextClassLoader[DockerClientProviderStrategy]

    val dockerImage = DockerImageName.parse("postgres").withTag("13.3")
    val container = new PostgreSQLContainer(dockerImage)

    // change the wait strategy because of https://github.com/testcontainers/testcontainers-java/issues/455
    val waitStrategy = new HostPortWaitStrategy
    container.setWaitStrategy(waitStrategy)

    container.withDatabaseName(database)
    container.withUsername("user")
    container.withPassword("password")
    container.withEnv("PGDATA", "/usr/share/postgres/data")
    container.addFileSystemBind(
      dataFolder.toString,
      "/usr/share/postgres",
      BindMode.READ_WRITE
    )
    container.start()
    container.getFirstMappedPort()
  }

  private def alreadyStarted(
      port: Int,
      database: String,
      logger: Logger
  ): Boolean = {
    // `CurrentThread.setContextClassLoader[org.postgresql.Driver]` should work but it does not
    CurrentThread.setContextClassLoader("org.postgresql.Driver")
    Try(
      DriverManager.getConnection(
        s"jdbc:postgresql://localhost:$port/$database",
        "user",
        "password"
      )
    ).fold(fa => { println(fa); false }, _ => true)
  }
}
