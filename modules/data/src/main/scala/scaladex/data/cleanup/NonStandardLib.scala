package scaladex.data
package cleanup

import java.nio.file._

import scala.io.Source
import scala.util.Using

import io.circe.parser._
import scaladex.infra.DataPaths

/**
 * Non standard published lib which misses the scala version in the artifact name
 * ex: scala-library
 *
 * @param groupId the group id
 * @param artifactId the artifact id
 * @param lookup where we can find the scala target
 */
case class NonStandardLib(
    groupId: String,
    artifactId: String,
    lookup: ScalaTargetLookup
)

sealed trait ScalaTargetLookup

/**
 * The version is encoded in the pom file
 * dependency on org.scala-lang:scala-library
 * ex: io.gatling : gatling-compiler : 2.2.2
 */
case object ScalaTargetFromPom extends ScalaTargetLookup

/**
 * The project is a plain-java project, thus no ScalaTarget.
 * ex: com.typesafe : config : 1.3.1
 */
case object NoScalaTargetPureJavaDependency extends ScalaTargetLookup

/**
 * The version is encoded in the version (ex: scala-library itself)
 */
case object ScalaTargetFromVersion extends ScalaTargetLookup

object NonStandardLib {

  /**
   * fetch non standard libs from json
   */
  def load(paths: DataPaths): List[NonStandardLib] = {
    val filePath = paths.nonStandard
    if (Files.exists(filePath)) {
      val input = Using.resource(Source.fromFile(filePath.toFile))(_.mkString)
      val nonStandard = decode[Map[String, String]](input).getOrElse(Map())

      nonStandard.map {
        case (artifact, rawLookup) =>
          val lookup =
            rawLookup match {
              case "pom"     => ScalaTargetFromPom
              case "java"    => NoScalaTargetPureJavaDependency
              case "version" => ScalaTargetFromVersion
              case _         => sys.error("unknown lookup: '" + rawLookup + "'")
            }

          val List(groupId, artifactId) = artifact.split(" ").toList

          NonStandardLib(groupId, artifactId, lookup)
      }.toList
    } else {
      List()
    }
  }
}
