package ch.epfl.scala.index.data.bintray

import java.nio.file.{Files, Path}

import ch.epfl.scala.index.data.LocalRepository
import ch.epfl.scala.index.data.LocalRepository.BintraySbtPlugins
import ch.epfl.scala.index.data.maven._
import jawn.support.json4s.Parser
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.joda.time.DateTime
import org.json4s.native.Serialization.write

import scala.util.{Success, Try}

case class SbtPluginsData(ivysData: Path) extends BintrayProtocol {

  /** @return Releases in the format expected by ProjectConvert */
  def load(): List[Try[(ReleaseModel, LocalRepository, String)]] = {
    read().map { release =>
      Success((release.releaseModel, BintraySbtPlugins, release.sha1))
    }
  }

  /** @return (releases, last-downlad-date) */
  def read(): List[SbtPluginReleaseModel] = {
    if (ivysData.toFile.exists())
      Parser
        .parseFromFile(ivysData.toFile)
        .get
        .extract[List[SbtPluginReleaseModel]]
    else Nil
  }

  def update(oldReleases: Seq[SbtPluginReleaseModel],
             newReleases: Seq[SbtPluginReleaseModel]): Unit = {
    val allReleases = (oldReleases ++ newReleases).distinct

    Files.write(
      ivysData,
      write[Seq[SbtPluginReleaseModel]](allReleases).getBytes
    )

    ()
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
