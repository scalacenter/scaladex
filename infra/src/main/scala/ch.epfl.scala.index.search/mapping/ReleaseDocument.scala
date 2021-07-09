package ch.epfl.scala.index.search.mapping

import ch.epfl.scala.index.model._
import ch.epfl.scala.index.model.release._

/**
 * A release document as it is stored in elasticsearch
 * For performance reason, the scala dependencies of the release are stored separately
 */
private[search] case class ReleaseDocument(
    id: Option[String],
    maven: MavenReference,
    reference: Release.Reference,
    resolver: Option[Resolver],
    name: Option[String],
    description: Option[String],
    released: Option[String],
    licenses: Set[License],
    isNonStandardLib: Boolean,
    liveData: Boolean,
    javaDependencies: Seq[JavaDependency],
    // this part for elasticsearch search
    targetType: String, // JVM, JS, Native, JAVA, SBT
    scalaVersion: Option[String],
    scalaJsVersion: Option[String],
    scalaNativeVersion: Option[String],
    sbtVersion: Option[String]
) {
  def toRelease: Release = Release(
    maven = maven,
    reference = reference,
    resolver = resolver,
    name = name,
    description = description,
    released = released,
    licenses = licenses,
    isNonStandardLib = isNonStandardLib,
    id = id,
    liveData = liveData,
    javaDependencies = javaDependencies,
    targetType = targetType,
    scalaVersion = scalaVersion,
    scalaJsVersion = scalaJsVersion,
    scalaNativeVersion = scalaNativeVersion,
    sbtVersion = sbtVersion
  )
}

private[search] object ReleaseDocument {
  def apply(release: Release): ReleaseDocument = ReleaseDocument(
    maven = release.maven,
    reference = release.reference,
    resolver = release.resolver,
    name = release.name,
    description = release.description,
    released = release.released,
    licenses = release.licenses,
    isNonStandardLib = release.isNonStandardLib,
    id = release.id,
    liveData = release.liveData,
    javaDependencies = release.javaDependencies,
    targetType = release.targetType,
    scalaVersion = release.scalaVersion,
    scalaJsVersion = release.scalaJsVersion,
    scalaNativeVersion = release.scalaNativeVersion,
    sbtVersion = release.sbtVersion
  )
}
