package ch.epfl.scala.index.newModel

import ch.epfl.scala.index.model.License
import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.Release
import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.release.Jvm
import ch.epfl.scala.index.model.release.MavenReference
import ch.epfl.scala.index.model.release.Resolver
import ch.epfl.scala.index.model.release.ScalaTarget
import ch.epfl.scala.index.model.release.ScalaTargetType
import ch.epfl.scala.index.newModel.NewProject.Organization
import ch.epfl.scala.index.newModel.NewProject.Repository
import ch.epfl.scala.index.newModel.NewRelease._
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

/**
 * Artifact release representation
 *
 * @param isNonStandardLib if not using artifactName_scalaVersion convention
 */

case class NewRelease(
    maven: MavenReference,
    version: SemanticVersion,
    organization: Organization,
    repository: Repository,
    artifact: ArtifactName,
    target: Option[ScalaTarget], // Todo: Include JAVA HERE and remove Option
    description: Option[String],
    released: Option[DateTime],
    resolver: Option[Resolver],
    licenses: Set[License],
    isNonStandardLib: Boolean
) {
  def targetType: ScalaTargetType = target.map(_.targetType).getOrElse(Jvm)

  def projectRef: Project.Reference =
    Project.Reference(organization.value, repository.value)

  def scalaVersion: Option[String] = ???

  def scalaJsVersion: Option[String] = ???

  def scalaNativeVersion: Option[String] = ???

  def sbtVersion: Option[String] = ???

  val reference: Release.Reference = Release.Reference(
    organization = organization.value,
    repository = repository.value,
    artifact = artifact.value,
    version = version,
    target = target
  )

}

object NewRelease {
  val format = ISODateTimeFormat.dateTime.withOffsetParsed
  def text(r: NewRelease) = r.maven.name
  case class ArtifactName(value: String) extends AnyVal {
    override def toString: String = value
  }

  def from(r: Release): NewRelease = {
    NewRelease(
      maven = r.maven,
      organization = Organization(r.reference.organization),
      repository = Repository(r.reference.repository),
      artifact = ArtifactName(r.reference.artifact),
      version = r.reference.version,
      target = r.reference.target,
      description = r.description,
      released = r.released.map(format.parseDateTime),
      resolver = r.resolver,
      licenses = r.licenses,
      isNonStandardLib = r.isNonStandardLib
    )
  }
}
