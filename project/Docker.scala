import org.testcontainers.DockerClientFactory
import org.testcontainers.dockerclient.DockerClientProviderStrategy

object Docker {
  lazy val client = {
    CurrentThread.setContextClassLoader[DockerClientProviderStrategy]
    DockerClientFactory.instance().client()
  }

  def kill(containerId: String): Unit =
    try client.killContainerCmd(containerId).exec()
    catch {
      case e: Throwable => 
            // Log the error
            println(s"Error killing container $containerId: ${e.getMessage}")
    }
}
