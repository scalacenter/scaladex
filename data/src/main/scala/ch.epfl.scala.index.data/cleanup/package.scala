package ch.epfl.scala.index
package data

import java.nio.file.Paths

package object cleanup {
  val cleanupIndexBase =
    build.info.BuildInfo.baseDirectory.toPath.resolve(Paths.get("contrib"))
}
