import sbt._

import org.testcontainers.dockerclient.DockerClientProviderStrategy
import org.testcontainers.utility.DockerImageName

import java.util.concurrent.TimeoutException
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.containers.BindMode

object Elasticsearch extends AutoPlugin {
  object autoImport {
    val elasticsearchDefaultPort =
      settingKey[Int]("Port of elasticserach instance")
    val elasticsearchFolder =
      settingKey[File]("Folder where elasticsearch data are stored")
    val startElasticsearch = taskKey[Int](
      "Chek that elasticsearch has already started or else start a container"
    )
  }

  import autoImport._

  def settings(defaultPort: Int): Seq[Setting[_]] = Seq(
    elasticsearchDefaultPort := defaultPort,
    elasticsearchFolder := Keys.baseDirectory.value / ".esdata",
    startElasticsearch := {
      import sbt.util.CacheImplicits._
      val dataFolder = elasticsearchFolder.value
      val defaultPort = elasticsearchDefaultPort.value
      val streams = Keys.streams.value
      val store = streams.cacheStoreFactory.make("last")
      val logger = streams.log
      val tracker = util.Tracked.lastOutput[Unit, Int](store) {
        case (_, None) =>
          checkOrStart(dataFolder, defaultPort, logger)
        case (_, Some(previousPort)) =>
          checkOrStart(dataFolder, previousPort, logger)
      }
      tracker(())
    }
  )

  private def checkOrStart(
      dataFolder: File,
      previousPort: Int,
      logger: Logger
  ): Int = {
    logger.info(s"Trying to connect to elasticsearch on port $previousPort")
    if (alreadyStarted(previousPort)) {
      logger.info(s"Elasticsearch has already started on port $previousPort")
      previousPort
    } else {
      logger.info("Trying to start elasticsearch container")
      val port = start(dataFolder)
      logger.info(
        s"Elasticsearch container successfully started with port $port"
      )
      port
    }
  }

  private def start(dataFolder: File): Int = {
    if (!dataFolder.exists) IO.createDirectory(dataFolder)
    IO.setPermissions(dataFolder, "rwxrwxrwx")

    CurrentThread.setContextClassLoader[DockerClientProviderStrategy]
    val image = DockerImageName
      .parse("docker.elastic.co/elasticsearch/elasticsearch")
      .withTag("7.16.1")
    val container = new ElasticsearchContainer(image)
    container.withEnv("discovery.type", "single-node")
    container.addFileSystemBind(
      dataFolder.toString,
      "/usr/share/elasticsearch/data",
      BindMode.READ_WRITE
    )
    container.start()
    container.getFirstMappedPort()
  }

  private def alreadyStarted(port: Int): Boolean = {
    val url = new URL(s"http://localhost:$port/")
    try {
      val connection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setConnectTimeout(100)
      connection.connect()
      val respCode = connection.getResponseCode
      if (respCode != HttpURLConnection.HTTP_OK)
        throw new MessageOnlyException(s"Got response code $respCode on $url")
      true
    } catch {
      case _: TimeoutException | _: IOException => false
    }
  }
}
