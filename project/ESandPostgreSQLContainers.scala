import org.testcontainers._
import sbt._
import sbt.Keys._

import scala.annotation.nowarn
import org.testcontainers.containers.{BindMode, FixedHostPortGenericContainer}
import org.testcontainers.containers.wait.strategy.{
  HttpWaitStrategy,
  LogMessageWaitStrategy
}
import org.testcontainers.dockerclient.DockerClientProviderStrategy
import org.testcontainers.utility.DockerImageName

import java.util.concurrent.TimeoutException
import java.net.HttpURLConnection
import java.net.HttpURLConnection._
import java.net.URL
import java.time.Duration
import java.io.IOException
import java.sql.DriverManager
import scala.util.{Failure, Success, Try}

object ESandPostgreSQLContainers extends AutoPlugin {
  object autoImport {
    val startESandPostgreSQL = taskKey[Unit](
      "Check that elasticsearch has already started or else start a container"
    )
  }

  import autoImport._

  override lazy val globalSettings: Seq[Setting[_]] = Seq(
    startESandPostgreSQL := {
      val esData = (ThisBuild / baseDirectory).value / ".esdata"
      val logger = streams.value.log

      if (esAlreadyStarted()) {
        logger.info("elasticsearch has already started")
      } else {
        logger.info("Trying to start elasticsearch container")
        startES(esData)
        logger.info("Elasticsearch container successfully started")
      }

      if (postgreSQLStarted()) {
        logger.info("PostgreSql already started")
      } else {
        logger.info("starting postgreSql")
        startPostreSqlContainer()
        logger.info("PostgreSql is started")
      }
    }
  )

  private def startES(esData: File): Unit = {
    if (!esData.exists) IO.createDirectory(esData)
    IO.setPermissions(esData, "rwxrwxrwx")

    @nowarn("cat=deprecation")
    val container = new FixedHostPortGenericContainer(
      "docker.elastic.co/elasticsearch/elasticsearch-oss:7.10.2"
    )
    container.withFixedExposedPort(9200, 9200)
    container.withEnv("discovery.type", "single-node")
    container.addFileSystemBind(
      esData.toString,
      "/usr/share/elasticsearch/data",
      BindMode.READ_WRITE
    )
    val esStarted = new HttpWaitStrategy()
      .forPort(9200)
      .forStatusCodeMatching(resp => resp == HTTP_OK)
      .withStartupTimeout(Duration.ofSeconds(30))
    container.setWaitStrategy(esStarted)

    workaroundToStartContainer()
    container.start()
  }

  private def startPostreSqlContainer(): Unit = {
    val version = "13.3"
    val database = "scaladex"
    val user = "user"
    val password = "password"
    val port = 5432

    val dockerImage = DockerImageName.parse("postgres").withTag(version)
    val waitStrategy = new LogMessageWaitStrategy()
      .withRegEx(".*database system is ready to accept connections.*\\s")
      .withTimes(2)
      .withStartupTimeout(Duration.ofSeconds(60))

    @nowarn("cat=deprecation")
    val container = new FixedHostPortGenericContainer(dockerImage.toString)
    container.withFixedExposedPort(port, port)
    container.withEnv("POSTGRES_DB", database)
    container.withEnv("POSTGRES_USER", user)
    container.withEnv("POSTGRES_PASSWORD", password)
    container.setWaitStrategy(waitStrategy)

    workaroundToStartContainer()
    container.start()

  }

  private def workaroundToStartContainer(): Unit = {
    // needed to resolve the DockerClientProviderStrategy in container.start
    val currentThread = Thread.currentThread()
    currentThread.setContextClassLoader(
      classOf[DockerClientProviderStrategy].getClassLoader()
    )
  }

  private def esAlreadyStarted(): Boolean = {
    val url = new URL("http://localhost:9200/")
    try {
      val connection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setConnectTimeout(100)
      connection.connect()
      val respCode = connection.getResponseCode
      if (respCode != HTTP_OK)
        throw new MessageOnlyException(s"Got response code $respCode on $url")
      true
    } catch {
      case _: TimeoutException | _: IOException => false
    }
  }
  private def postgreSQLStarted(): Boolean = {
    val currentThread = Thread.currentThread()
    currentThread.setContextClassLoader(
      Class.forName("org.postgresql.Driver").getClassLoader()
    )
    Try(
      DriverManager.getConnection(
        "jdbc:postgresql://localhost:5432/scaladex",
        "user",
        "password"
      )
    ) match {
      case Success(value) => true
      case Failure(_) => false
    }
  }
}
