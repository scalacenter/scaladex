package ch.epfl.scala.index.data.bintray

import java.nio.file.Files
import java.nio.file.Path

import ch.epfl.scala.index.data.maven._
import ch.epfl.scala.services.storage.LocalRepository
import ch.epfl.scala.services.storage.LocalRepository.BintraySbtPlugins
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.joda.time.DateTime
import org.json4s.native.Serialization.write
import org.typelevel.jawn.support.json4s.Parser

case class SbtPluginsData(ivysData: Path) extends BintrayProtocol {

  /** @return Releases in the format expected by ProjectConvert */
  def iterator: Iterator[(ReleaseModel, LocalRepository, String)] = {
    read().iterator.map { release =>
      (release.releaseModel, BintraySbtPlugins, release.sha1)
    }
  }

  def read(): List[SbtPluginReleaseModel] = {
    if (ivysData.toFile.exists())
      Parser
        .parseFromFile(ivysData.toFile)
        .get
        .extract[List[SbtPluginReleaseModel]]
    else Nil
  }

  def update(
      oldReleases: Seq[SbtPluginReleaseModel],
      newReleases: Seq[SbtPluginReleaseModel]
  ): Unit = {
    // use sha1 to uniquely identify a release
    val oldReleaseMap = oldReleases.map(r => r.sha1 -> r).toMap
    val allReleaseMap = newReleases.foldLeft(oldReleaseMap) {
      case (acc, release) =>
        // replace an old release with the new release based on the sha1
        acc + (release.sha1 -> release)
    }
    val allReleases = allReleaseMap.values.toSeq

    Files.write(
      ivysData,
      write[Seq[SbtPluginReleaseModel]](allReleases).getBytes
    )
  }

}

case class SbtPluginReleaseModel(
    releaseModel: ReleaseModel,
    created: String,
    sha1: String
)

object SbtPluginReleaseModel {
  def apply(
      subject: String,
      repo: String,
      scmUrl: Option[String],
      organization: String,
      artifact: String,
      scalaVersion: String,
      sbtVersion: String,
      version: String,
      ivyPath: String,
      sha1: String,
      descriptor: ModuleDescriptor
  ): SbtPluginReleaseModel = {
    val releaseModel = ReleaseModel(
      groupId = organization,
      artifactId = artifact,
      version = version,
      packaging = "jar",
      description = Option(descriptor.getDescription),
      scm = Some(SourceCodeManagment(None, None, scmUrl, None)),
      dependencies = descriptor.getDependencies.toList.map { dependency =>
        val dep = dependency.getDependencyRevisionId
        Dependency(
          dep.getOrganisation,
          dep.getName,
          dep.getRevision,
          scope = /* TODO */ None,
          exclusions = dependency.getAllExcludeRules
            .map(_.getId.getModuleId)
            .map(rule => Exclusion(rule.getOrganisation, rule.getName))
            .toSet
        )
      },
      sbtPluginTarget = Some(
        SbtPluginTarget(scalaVersion, sbtVersion)
      )
    )

    val publicationDate = new DateTime(descriptor.getPublicationDate).toString
    new SbtPluginReleaseModel(releaseModel, publicationDate, sha1)
  }

}
