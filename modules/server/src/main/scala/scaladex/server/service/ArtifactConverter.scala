package scaladex.server.service

import java.time.Instant

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.ArtifactDependency.Scope
import scaladex.core.model._
import scaladex.data.cleanup._
import scaladex.data.maven.ArtifactModel
import scaladex.data.maven.Dependency
import scaladex.data.maven.SbtPluginTarget
import scaladex.infra.DataPaths

private case class ArtifactMeta(
    artifactName: String,
    binaryVersion: BinaryVersion,
    isNonStandard: Boolean
) {
  def artifactId: String = if (isNonStandard) artifactName else s"$artifactName${binaryVersion.label}"
}

class ArtifactConverter(paths: DataPaths) extends LazyLogging {
  private val nonStandardLibs = NonStandardLib.load(paths)

  def convert(
      pom: ArtifactModel,
      projectRef: Project.Reference,
      creationDate: Instant
  ): Option[(Artifact, Seq[ArtifactDependency])] =
    for {
      version <- SemanticVersion.parse(pom.version)
      meta <- extractMeta(pom)
    } yield {
      val artifact = Artifact(
        Artifact.GroupId(pom.groupId),
        meta.artifactId,
        version,
        Artifact.Name(meta.artifactName),
        projectRef,
        pom.description,
        creationDate,
        None,
        pom.licenses.flatMap(l => License.get(l.name)).toSet,
        meta.isNonStandard,
        meta.binaryVersion.platform,
        meta.binaryVersion.language,
        extractScalaVersion(pom)
      )
      val dependencies = pom.dependencies.map { dep =>
        ArtifactDependency(
          artifact.mavenReference,
          dep.mavenRef,
          dep.scope.map(Scope.apply).getOrElse(Scope.compile)
        )
      }.distinct
      (artifact, dependencies)
    }

  private def extractScalaVersion(pom: ArtifactModel): Option[SemanticVersion] = {
    val scalaDependencies = pom.dependencies.filter { dep =>
      dep.groupId == "org.scala-lang" &&
      (dep.artifactId == "scala-library" || dep.artifactId == "scala3-library_3")
    }

    val sortedScalaDependencies = scalaDependencies.sortBy(_.artifactId)
    val fullScalaVersion = scalaDependencies match {
      case _ :: _ :: _ => Some(sortedScalaDependencies.last.version)
      case head :: Nil => Some(head.version)
      case _           => None
    }

    fullScalaVersion.flatMap(SemanticVersion.parse)
  }

  /**
   * artifactId is often use to express binary compatibility with a scala version (ScalaTarget)
   * if the developer follow this convention we extract the relevant parts and we mark
   * the library as standard. Otherwise we either have a library like gatling or the scala library itself
   *
   * @return The artifact name (without suffix), the binary version, whether this project is a standard Scala library or not
   */
  private def extractMeta(pom: ArtifactModel): Option[ArtifactMeta] = {
    val nonStandardLookup =
      nonStandardLibs
        .find(lib => lib.groupId == pom.groupId && lib.artifactId == pom.artifactId)
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
                    artifactName = pom.artifactId.stripSuffix(s"_${rawScalaVersion}_$rawSbtVersion"),
                    binaryVersion = BinaryVersion(SbtPlugin(sbtVersion), Scala(scalaVersion)),
                    isNonStandard = false
                  )
                )
              case _ =>
                logger.error(
                  s"Unable to decode the Scala target: $rawScalaVersion $rawSbtVersion"
                )
                None
            }
        }

      // For example: io.gatling
      case Some(BinaryVersionLookup.FromDependency) =>
        for {
          dep: Dependency <- pom.dependencies.find { dep =>
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
      case Some(BinaryVersionLookup.Java) =>
        Some(
          ArtifactMeta(
            artifactName = pom.artifactId,
            binaryVersion = BinaryVersion(Jvm, Java),
            isNonStandard = true
          )
        )

      // For example: scala-compiler
      case Some(BinaryVersionLookup.FromArtifactVersion) =>
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
