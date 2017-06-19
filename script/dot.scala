import ch.epfl.scala.index._
import data._

import project._
import maven.PomsReader
import scala.util.Success

object A {
  val newData = ProjectConvert(
    PomsReader.load().collect { case Success(pomAndMeta) => pomAndMeta }
  )

  val (projects, projectReleases) = newData.unzip
  val releases = projectReleases.flatten

  val testOrLogging = Set(
    "akka/akka-slf4j",
    "scala/scala-library",
    "scopt/scopt",
    "typesafehub/scala-logging",
    "typesafehub/scala-logging-slf4j"
  )

  val links = releases
    .flatMap(release =>
      release.scalaDependencies
        .filter(_.scope != Some("test"))
        .filter(d => !testOrLogging.contains(d.reference.name))
        .map(dep =>
          release.reference.projectReference -> dep.reference.projectReference))
    .toSet

  val nl = System.lineSeparator
  val linksOut = links
    .map {
      case (model.Project.Reference(o1, r1),
            model.Project.Reference(o2, r2)) =>
        s"""  "$o1/$r1" -> "$o2/$r2";"""
    }
    .mkString("digraph G{" + nl, nl, nl + "}")

  import java.nio.file._
  import java.nio.charset.StandardCharsets

  Files.write(Paths.get("out2.dot"), linksOut.getBytes(StandardCharsets.UTF_8))
}
