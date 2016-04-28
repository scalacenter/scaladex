package ch.epfl.scala.index

import java.nio.file.Paths

package object bintray {
  val nl = System.lineSeparator
  val bintrayIndexBase = build.info.BuildInfo.baseDirectory.toPath.resolve(Paths.get("index", "bintray"))
  val bintrayCheckpoint = bintrayIndexBase.resolve("bintray.json")
  val bintrayPomBase = bintrayIndexBase.resolve("poms_sha")
}