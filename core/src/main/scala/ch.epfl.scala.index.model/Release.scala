package ch.epfl.scala.index.model

import ch.epfl.scala.index.model.release._

/**
 * Artifact release representation
 * @param maven famous maven triple: org.typelevel - cats-core_sjs0.6_2.11 - 0.6.0
 * @param reference similar to maven but with a clean artifact name
 * @param name human readable name (ex: Apache Spark)
 * @param resolver if not on maven central (ex: Bintray)
 * @param description the description of that release
 * @param released first release date
 * @param licenses a bunch of licences
 * @param isNonStandardLib if not using artifactName_scalaVersion convention
 * @param id Elastic search id
 * @param javaDependencies bunch of java dependencies
 */
case class Release(
    maven: MavenReference,
    reference: Release.Reference,
    resolver: Option[Resolver],
    name: Option[String], // artifact name
    description: Option[String],
    released: Option[String],
    licenses: Set[License],
    isNonStandardLib: Boolean,
    id: Option[String],
    liveData: Boolean,
    // TODO replace all fields below by ScalaTarget data type
    targetType: String, // JVM, JS, Native, JAVA, SBT
    scalaVersion: Option[String],
    scalaJsVersion: Option[String],
    scalaNativeVersion: Option[String],
    sbtVersion: Option[String]
) {

  def isValid: Boolean =
    reference.isValid

  def isScalaLib: Boolean = reference.isScalaLib
}

object Release {

  /**
   * @param organization (ex: typelevel | akka)
   * @param repository (ex: cats | akka)
   * @param artifact (ex: cats-core | akka-http-experimental)
   */
  case class Reference(
      organization: String,
      repository: String,
      artifact: String,
      version: SemanticVersion,
      target: Platform
  ) {

    def isValid: Boolean = {
      target.isValid
    }

    def projectReference: Project.Reference =
      Project.Reference(organization, repository)

    def isScalaLib: Boolean = {
      organization == "scala" &&
      repository == "scala" &&
      artifact == "scala-library"
    }
  }
}
