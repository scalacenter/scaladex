package ch.epfl.scala.index
package data

import java.nio.file.Paths

package object github {
  val githubIndexBase = 
    build.info.BuildInfo.baseDirectory.toPath.resolve(Paths.get("index", "github"))
}