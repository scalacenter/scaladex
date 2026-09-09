import sbt._

import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Volume
import org.testcontainers.dockerclient.DockerClientProviderStrategy
import org.testcontainers.utility.DockerImageName

import java.util.concurrent.TimeoutException
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException
import org.testcontainers.elasticsearch.ElasticsearchContainer
import scala.collection.mutable
import scala.collection.concurrent.TrieMap
import java.nio.file.Path

object Elasticsearch extends AutoPlugin {
  private val containers: mutable.Map[Path, ElasticsearchContainer] = TrieMap.empty

  object autoImport {
    val startElasticsearch = taskKey[Int]("Connect to Elasticsearch or start an Elasticsearch container")
  }

  import autoImport._

  def settings(defaultPort: Int): Seq[Setting[?]] = Seq(
    startElasticsearch := {
      import sbt.util.CacheImplicits._
      val dataFolder = Keys.baseDirectory.value / ".esdata"
      val streams = Keys.streams.value
      val logger = streams.log
      if (canConnect(defaultPort)) {
        logger.info(s"Elasticsearch available on port $defaultPort")
        defaultPort
      } else {
        // we cache the container to reuse it after a reload
        val store = streams.cacheStoreFactory.make("container")
        val tracker = util.Tracked.lastOutput[Unit, (String, Int)](store) {
          case (_, None) =>
            startContainer(dataFolder, logger)
          case (_, Some((containerId, port))) =>
            if (canConnect(port)) {
              logger.info(s"Elasticsearch container already started on port $port")
              (containerId, port)
            } else {
              Docker.kill(containerId)
              startContainer(dataFolder, logger)
            }
        }
        tracker(())._2
      }
    },
    Keys.clean := {
      Keys.clean.value
      val dataFolder = Keys.baseDirectory.value / ".esdata"
      containers.get(dataFolder.toPath).foreach(_.close())
      containers.remove(dataFolder.toPath)
    }
  )

  private def startContainer(dataFolder: File, logger: Logger): (String, Int) = {
    CurrentThread.setContextClassLoader[DockerClientProviderStrategy]
    val image = DockerImageName
      .parse("docker.elastic.co/elasticsearch/elasticsearch")
      .withTag("8.19.15")
    val volumeName = s"scaladex-elasticsearch-data-${Math.abs(dataFolder.toString.hashCode)}"
    val container = new ElasticsearchContainer(image)
    container
      .withEnv("discovery.type", "single-node")
      .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
      .withEnv("xpack.security.enabled", "false")
      .withCreateContainerCmdModifier { cmd =>
        cmd.getHostConfig.withBinds(new Bind(volumeName, new Volume("/usr/share/elasticsearch/data")))
      }
    // container.withLogConsumer(frame => logger.info(frame.getUtf8StringWithoutLineEnding))
    val port =
      try {
        container.start()
        container.getFirstMappedPort()
      } catch {
        case e: Throwable =>
          logger.error(s"Error starting Elasticsearch container: {$e}")
          container.stop()
          throw e
      }
    logger.info(s"Elasticsearch container started on port $port")
    containers(dataFolder.toPath) = container
    (container.getContainerId, port)
  }

  private def canConnect(port: Int): Boolean = {
    val url = new URL(s"http://localhost:$port/")
    try {
      val connection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setConnectTimeout(200)
      connection.connect()
      val respCode = connection.getResponseCode
      if (respCode != HttpURLConnection.HTTP_OK)
        throw new MessageOnlyException(s"Got response code $respCode on $url")
      connection.disconnect()
      true
    } catch {
      case _: TimeoutException | _: IOException => false
    }
  }
}
