package scaladex.server.service

import java.time.Instant

import scaladex.core.model.ArtifactDependency.Scope
import scaladex.core.model.*
import scaladex.data.cleanup.*
import scaladex.data.maven.ArtifactModel
import scaladex.data.maven.Dependency
import scaladex.data.maven.SbtPluginTarget
import scaladex.infra.DataPaths

import com.typesafe.scalalogging.LazyLogging

private case class ArtifactMeta(artifactId: Artifact.ArtifactId, isNonStandard: Boolean)

class ArtifactConverter(paths: DataPaths) extends LazyLogging:
  private val nonStandardLibs = NonStandardLib.load(paths)

  def convert(
      pom: ArtifactModel,
      projectRef: Project.Reference,
      creationDate: Instant
  ): Option[(Artifact, Seq[ArtifactDependency])] =
    extractMeta(pom).map { meta =>
      val artifact = Artifact(
        Artifact.GroupId(pom.groupId),
        meta.artifactId,
        Version(pom.version),
        projectRef,
        pom.description,
        creationDate,
        None,
        pom.licenses.flatMap(l => License.get(l.name)).toSet,
        meta.isNonStandard,
        extractScalaVersion(pom),
        pom.scaladocUrl,
        pom.versionScheme,
        pom.developers.distinct
      )
      val dependencies = pom.dependencies.map { dep =>
        val scope = dep.scope.map(Scope.apply).getOrElse(Scope.compile)
        ArtifactDependency(artifact.reference, dep.reference, scope)
      }.distinct
      (artifact, dependencies)
    }

  private def extractScalaVersion(pom: ArtifactModel): Option[Version] =
    val scalaDependencies = pom.dependencies.filter { dep =>
      dep.groupId == "org.scala-lang" &&
      (dep.artifactId == "scala-library" || dep.artifactId == "scala3-library_3")
    }
    val fullScalaVersion = scalaDependencies.sortBy(_.artifactId).lastOption.map(_.version)
    fullScalaVersion.flatMap(Version.parseSemantically)

  /** artifactId is often use to express binary compatibility with a scala version (ScalaTarget) if the developer follow
    * this convention we extract the relevant parts and we mark the library as standard. Otherwise we either have a
    * library like gatling or the scala library itself
    *
    * @return
    *   The artifact name (without suffix), the binary version, whether this project is a standard Scala library or not
    */
  private def extractMeta(pom: ArtifactModel): Option[ArtifactMeta] =
    val nonStandardLookup =
      nonStandardLibs
        .find(lib => lib.groupId == pom.groupId && lib.artifactId == pom.artifactId)
        .map(_.lookup)

    val artifactMetaOption = nonStandardLookup match
      case None =>
        pom.sbtPluginTarget match

          // This is a usual Scala library (whose artifact name is suffixed by the Scala binary version)
          // For example: akka-actors_2.12
          case None =>
            Some(ArtifactMeta(Artifact.ArtifactId(pom.artifactId), isNonStandard = false))

          // Or it can be an sbt-plugin published as a maven style. In such a case the Scala target
          // is not suffixed to the artifact name but can be found in the model’s `sbtPluginTarget` member.
          case Some(SbtPluginTarget(rawScalaVersion, rawSbtVersion)) =>
            Version
              .parseSemantically(rawScalaVersion)
              .zip(Version.parseSemantically(rawSbtVersion))
              .map {
                case (scalaVersion, sbtVersion) =>
                  val name = Artifact.Name(pom.artifactId.stripSuffix(s"_${rawScalaVersion}_$rawSbtVersion"))
                  val binaryVersion = BinaryVersion(SbtPlugin(sbtVersion), Scala(scalaVersion))
                  ArtifactMeta(Artifact.ArtifactId(name, binaryVersion), isNonStandard = false)
              }
              .orElse {
                logger.error(s"Unable to decode the Scala target: $rawScalaVersion $rawSbtVersion")
                None
              }

      // For example: io.gatling
      case Some(BinaryVersionLookup.FromDependency) =>
        for
          dep: Dependency <- pom.dependencies.find { dep =>
            dep.groupId == "org.scala-lang" &&
            (dep.artifactId == "scala-library" || dep.artifactId == "scala3-library_3")
          }
          version <- Version.parseSemantically(dep.version)
        yield
          val name = Artifact.Name(pom.artifactId)
          val binaryVersion = BinaryVersion(Jvm, Scala.fromFullVersion(version))
          ArtifactMeta(Artifact.ArtifactId(name, binaryVersion), isNonStandard = true)
      // For example: typesafe config
      case Some(BinaryVersionLookup.Java) =>
        Some(
          ArtifactMeta(
            Artifact.ArtifactId(Artifact.Name(pom.artifactId), BinaryVersion(Jvm, Java)),
            isNonStandard = true
          )
        )

      // For example: scala-compiler
      case Some(BinaryVersionLookup.FromArtifactVersion) =>
        for version <- Version.parseSemantically(pom.version)
        yield ArtifactMeta(
          Artifact.ArtifactId(Artifact.Name(pom.artifactId), BinaryVersion(Jvm, Scala.fromFullVersion(version))),
          isNonStandard = true
        )
    // we need to filter out binary versions that are not valid
    artifactMetaOption.filter(_.artifactId.binaryVersion.isValid)
  end extractMeta
end ArtifactConverter
