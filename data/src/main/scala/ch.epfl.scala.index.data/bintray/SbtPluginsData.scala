package ch.epfl.scala.index.data.bintray

import java.nio.file.Files

import ch.epfl.scala.index.data.LocalRepository.BintraySbtPlugins
import ch.epfl.scala.index.data.maven._
import ch.epfl.scala.index.data.{DataPaths, LocalRepository}
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write

import scala.util.{Success, Try}

case class SbtPluginsData(paths: DataPaths) extends BintrayProtocol {

  /** @return Releases in the format expected by ProjectConvert */
  def load(): List[Try[(ReleaseModel, LocalRepository, String)]] = {
    val (releases, _) = read()

    releases.map { release =>
      Success((release.releaseModel, BintraySbtPlugins, release.sha1))
    }
  }

  /** @return (releases, last-downlad-date) */
  def read(): (List[SbtPluginReleaseModel], Option[String]) = {

    val releases =
      if (paths.ivysData.toFile.exists())
        parse(new String(Files.readAllBytes(paths.ivysData)))
          .extract[List[SbtPluginReleaseModel]]
      else Nil

    val lastDownload =
      if (paths.ivysLastDownload.toFile.exists())
        Some(new String(Files.readAllBytes(paths.ivysLastDownload)))
      else None

    (releases, lastDownload)
  }

  def update(oldReleases: List[SbtPluginReleaseModel],
             newReleases: List[SbtPluginReleaseModel]): Unit = {
    val allReleases = (oldReleases ++ newReleases).distinct

    val _ = Files.write(
      paths.ivysData,
      write[List[SbtPluginReleaseModel]](allReleases).getBytes
    )
  }

}

case class SbtPluginReleaseModel(
    releaseModel: ReleaseModel,
    created: String,
    sha1: String
)
