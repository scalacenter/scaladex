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
      case _: Throwable => ()
    }
}
