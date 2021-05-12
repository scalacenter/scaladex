import sbt._
import sbt.Keys._
import sbt.dsl.LinterLevel.Ignore

import scala.annotation.nowarn

import org.testcontainers.containers.BindMode
import org.testcontainers.containers.FixedHostPortGenericContainer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import org.testcontainers.dockerclient.DockerClientProviderStrategy

import java.util.concurrent.TimeoutException
import java.lang.Thread
import java.net.HttpURLConnection
import java.net.HttpURLConnection._
import java.net.URL
import java.time.Duration
import java.io.IOException

object Elasticsearch extends AutoPlugin {
  object autoImport {
    val startElasticsearch = taskKey[Unit](
      "Check that elasticsearch has already started or else start a container"
    )
  }

  import autoImport._

  override lazy val globalSettings: Seq[Setting[_]] = Seq(
    startElasticsearch := {
      val esData = (ThisBuild / baseDirectory).value / ".esdata"
      val logger = streams.value.log
      if (alreadyStarted) {
        logger.info("elasticsearch has already started")
      } else {
        logger.info("Trying to start elasticsearch container")
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

        // needed to resolve the DockerClientProviderStrategy in container.start
        val currentThread = Thread.currentThread()
        currentThread.setContextClassLoader(
          classOf[DockerClientProviderStrategy].getClassLoader()
        )

        container.start()
        logger.info("Elasticsearch container successfully started")
      }
      ()
    }
  )

  private def alreadyStarted(): Boolean = {
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
}
