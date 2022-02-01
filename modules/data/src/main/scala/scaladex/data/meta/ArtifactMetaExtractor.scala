package scaladex.data.meta

import org.slf4j.LoggerFactory
import scaladex.core.model.Artifact
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Java
import scaladex.core.model.Jvm
import scaladex.core.model.SbtPlugin
import scaladex.core.model.Scala
import scaladex.core.model.SemanticVersion
import scaladex.data.cleanup._
import scaladex.data.maven.ArtifactModel
import scaladex.data.maven.SbtPluginTarget
import scaladex.infra.DataPaths

case class ArtifactMeta(
    artifactName: String,
    binaryVersion: BinaryVersion,
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
  def extract(pom: ArtifactModel): Option[ArtifactMeta] = {
    val nonStandardLookup =
      nonStandardLibs
        .find(lib =>
          lib.groupId == pom.groupId &&
            lib.artifactId == pom.artifactId
        )
        .map(_.lookup)

    val artifactMetaOption = nonStandardLookup match {
      case None =>
        pom.sbtPluginTarget match {

          // This is a usual Scala library (whose artifact name is suffixed by the Scala binary version)
          // For example: akka-actors_2.12
          case None =>
            Artifact.ArtifactId.parse(pom.artifactId).map {
              case Artifact.ArtifactId(artifactName, binaryVersion) =>
                ArtifactMeta(
                  artifactName = artifactName.value,
                  binaryVersion = binaryVersion,
                  isNonStandard = false
                )
            }

          // Or it can be an sbt-plugin published as a maven style. In such a case the Scala target
          // is not suffixed to the artifact name but can be found in the model’s `sbtPluginTarget` member.
          case Some(SbtPluginTarget(rawScalaVersion, rawSbtVersion)) =>
            SemanticVersion
              .parse(rawScalaVersion)
              .zip(SemanticVersion.parse(rawSbtVersion)) match {
              case Some((scalaVersion, sbtVersion)) =>
                Some(
                  ArtifactMeta(
                    artifactName = pom.artifactId,
                    binaryVersion = BinaryVersion(SbtPlugin(sbtVersion), Scala(scalaVersion)),
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
            (dep.artifactId == "scala-library" || dep.artifactId == "scala3-library_3")
          }
          version <- SemanticVersion.parse(dep.version)
        } yield
        // we assume binary compatibility
        ArtifactMeta(
          artifactName = pom.artifactId,
          binaryVersion = BinaryVersion(Jvm, Scala.fromFullVersion(version)),
          isNonStandard = true
        )
      // For example: typesafe config
      case Some(NoScalaTargetPureJavaDependency) =>
        Some(
          ArtifactMeta(
            artifactName = pom.artifactId,
            binaryVersion = BinaryVersion(Jvm, Java),
            isNonStandard = true
          )
        )

      // For example: scala-compiler
      case Some(ScalaTargetFromVersion) =>
        for (version <- SemanticVersion.parse(pom.version))
          yield ArtifactMeta(
            artifactName = pom.artifactId,
            binaryVersion = BinaryVersion(Jvm, Scala.fromFullVersion(version)),
            isNonStandard = true
          )
    }
    // we need to filter out binary versions that are not valid
    artifactMetaOption.filter(_.binaryVersion.isValid)
  }
}
