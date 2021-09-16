package ch.epfl.scala.index
package data

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import ch.epfl.scala.services.storage.DataPaths
import ch.epfl.scala.index.model.Descending
import ch.epfl.scala.services.storage.DataPaths
import ch.epfl.scala.services.storage.LocalPomRepository
import com.github.nscala_time.time.Imports._
import org.joda.time.DateTime
import org.json4s._
import org.json4s.native.Serialization.{write => swrite}
import org.typelevel.jawn.support.json4s.Parser

case class Meta(
    sha1: String,
    path: String,
    created: DateTime
)

object Meta {

  /**
   * MetaSerializer to keep the fields ordering
   */
  object MetaSerializer
      extends CustomSerializer[Meta](format =>
        (
          {
            case in: JValue => {
              implicit val formats = DefaultFormats ++ Seq(
                DateTimeSerializer
              )
              in.extract[Meta]
            }
          },
          {
            case meta: Meta => {
              implicit val formats = DefaultFormats ++ Seq(
                DateTimeSerializer
              )
              JObject(
                JField("created", Extraction.decompose(meta.created)),
                JField("path", Extraction.decompose(meta.path)),
                JField("sha1", Extraction.decompose(meta.sha1))
              )
            }
          }
        )
      )

  implicit val formats: Formats =
    DefaultFormats ++ Seq(DateTimeSerializer, MetaSerializer)
  implicit val serialization: org.json4s.native.Serialization.type =
    native.Serialization

  def load(paths: DataPaths, repository: LocalPomRepository): List[Meta] = {
    assert(
      repository == LocalPomRepository.MavenCentral ||
        repository == LocalPomRepository.UserProvided
    )

    val metaPath = paths.meta(repository)
    val metaRaw = new String(Files.readAllBytes(metaPath))

    metaRaw
      .split('\n')
      .toList
      .filter(_ != "")
      .map(json => Parser.parseUnsafe(json).extract[Meta])
      .sortBy(_.created)(Descending)
  }

  def append(
      paths: DataPaths,
      meta: Meta,
      repository: LocalPomRepository
  ): Unit = {
    val all = load(paths, repository)
    write(paths, meta :: all, repository)
  }

  def write(
      paths: DataPaths,
      metas: List[Meta],
      repository: LocalPomRepository
  ): Unit = {
    val sorted = metas.sortBy(_.created)(Descending)
    val jsonPerLine =
      sorted
        .map(s => swrite(s))
        .mkString("", "\n", "\n")

    val metaPath = paths.meta(repository)

    if (Files.exists(metaPath)) {
      Files.delete(metaPath)
    }

    Files.write(metaPath, jsonPerLine.getBytes(StandardCharsets.UTF_8))
    ()
  }
}
