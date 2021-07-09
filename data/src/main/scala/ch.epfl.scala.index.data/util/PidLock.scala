package ch.epfl.scala.index
package data
package util

import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

object PidLock {
  def create(prefix: String): Unit = {
    val pid = ManagementFactory.getRuntimeMXBean().getName().split("@").head
    val pidFile = Paths.get(s"$prefix-PID")
    Files.write(pidFile, pid.getBytes(StandardCharsets.UTF_8))
    sys.addShutdownHook {
      Files.delete(pidFile)
    }

    ()
  }
}
