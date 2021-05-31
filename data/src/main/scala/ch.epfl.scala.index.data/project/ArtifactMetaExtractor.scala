package ch.epfl.scala.index
package data
package project

import cleanup._
import model.release.{
  LanguageVersion,
  BinaryVersion,
  SbtPlugin,
  ScalaJvm,
  ScalaTarget
}
import model.{Artifact, SemanticVersion}
import maven.{ReleaseModel, SbtPluginTarget}
import org.slf4j.LoggerFactory

case class ArtifactMeta(
    artifactName: String,
    scalaTarget: Option[ScalaTarget],
    isNonStandard: Boolean
)

class ArtifactMetaExtractor(paths: DataPaths) {
  private val log = LoggerFactory.getLogger(getClass)

  private val nonStandardLibs = NonStandardLib.load(paths)

  /**
   * artifactId is often use to express binary compatibility with a scala version (ScalaTarget)
   * if the developer follow this convention we extract the relevant parts and we mark
   * the library as standard. Otherwise we either have a library like gatling or the scala library itself
   *
   * @return The artifact name (without suffix), the Scala target, whether this project is a usual Scala library or not
   */
  def extractMeta(pom: ReleaseModel): Option[ArtifactMeta] = {
    val nonStandardLookup =
      nonStandardLibs
        .find(lib =>
          lib.groupId == pom.groupId &&
            lib.artifactId == pom.artifactId
        )
        .map(_.lookup)

    nonStandardLookup match {
      case None =>
        pom.sbtPluginTarget match {

          // This is a usual Scala library (whose artifact name is suffixed by the Scala binary version)
          // For example: akka-actors_2.12
          case None =>
            Artifact.parse(pom.artifactId).map {
              case Artifact(artifactName, target) =>
                ArtifactMeta(
                  artifactName = artifactName,
                  scalaTarget = Some(target),
                  isNonStandard = false
                )
            }

          // Or it can be an sbt-plugin published as a maven style. In such a case the Scala target
          // is not suffixed to the artifact name but can be found in the model’s `sbtPluginTarget` member.
          case Some(SbtPluginTarget(rawScalaVersion, rawSbtVersion)) =>
            LanguageVersion
              .tryParse(rawScalaVersion)
              .zip(
                BinaryVersion.parse(rawSbtVersion)
              ) match {
              case Some((scalaVersion, sbtVersion)) =>
                Some(
                  ArtifactMeta(
                    artifactName = pom.artifactId,
                    scalaTarget = Some(SbtPlugin(scalaVersion, sbtVersion)),
                    isNonStandard = false
                  )
                )
              case _ =>
                log.error(
                  s"Unable to decode the Scala target: $rawScalaVersion $rawSbtVersion"
                )
                None
            }
        }

      // For example: io.gatling
      case Some(ScalaTargetFromPom) =>
        for {
          dep <- pom.dependencies.find { dep =>
            dep.groupId == "org.scala-lang" &&
            dep.artifactId == "scala-library"
          }
          version <- SemanticVersion.tryParse(dep.version)
          target <- ScalaJvm.fromFullVersion(version)
        } yield {
          // we assume binary compatibility
          ArtifactMeta(
            artifactName = pom.artifactId,
            scalaTarget = Some(target),
            isNonStandard = true
          )
        }
      // For example: typesafe config
      case Some(NoScalaTargetPureJavaDependency) =>
        Some(
          ArtifactMeta(
            artifactName = pom.artifactId,
            scalaTarget = None,
            isNonStandard = true
          )
        )

      // For example: scala-compiler
      case Some(ScalaTargetFromVersion) =>
        for {
          version <- SemanticVersion.tryParse(pom.version)
          target <- ScalaJvm.fromFullVersion(version)
        } yield ArtifactMeta(
          artifactName = pom.artifactId,
          scalaTarget = ScalaJvm.fromFullVersion(version),
          isNonStandard = true
        )
    }
  }
}
