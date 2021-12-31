package scaladex.data.bintray

import java.nio.file.Files
import java.nio.file.Path

import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.joda.time.DateTime
import org.json4s.native.Serialization.write
import org.typelevel.jawn.support.json4s.Parser
import scaladex.data.maven._
import scaladex.infra.storage.LocalRepository
import scaladex.infra.storage.LocalRepository.BintraySbtPlugins

case class SbtPluginsData(ivysData: Path) extends BintrayProtocol {

  def iterator: Iterator[(ArtifactModel, LocalRepository, String)] =
    read().iterator.map(artifact => (artifact.artifactModel, BintraySbtPlugins, artifact.sha1))

  def read(): List[SbtPluginArtifactModel] =
    if (ivysData.toFile.exists())
      Parser
        .parseFromFile(ivysData.toFile)
        .get
        .extract[List[SbtPluginArtifactModel]]
    else Nil

  def update(
      oldArtifacts: Seq[SbtPluginArtifactModel],
      newArtifacts: Seq[SbtPluginArtifactModel]
  ): Unit = {
    // use sha1 to uniquely identify a artifact
    val oldArtifactsMap = oldArtifacts.map(r => r.sha1 -> r).toMap
    val allArtifactsMap = newArtifacts.foldLeft(oldArtifactsMap) {
      case (acc, artifact) =>
        // replace an old artifact with the new artifact based on the sha1
        acc + (artifact.sha1 -> artifact)
    }
    val allArtifacts = allArtifactsMap.values.toSeq

    Files.write(
      ivysData,
      write[Seq[SbtPluginArtifactModel]](allArtifacts).getBytes
    )
  }

}

case class SbtPluginArtifactModel(
    artifactModel: ArtifactModel,
    created: String,
    sha1: String
)

object SbtPluginArtifactModel {
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
  ): SbtPluginArtifactModel = {
    val artifactModel = ArtifactModel(
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
    new SbtPluginArtifactModel(artifactModel, publicationDate, sha1)
  }

}
