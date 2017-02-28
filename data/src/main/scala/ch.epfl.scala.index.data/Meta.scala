package ch.epfl.scala.index
package data

import model.Descending
import com.github.nscala_time.time.Imports._

import org.joda.time.DateTime

import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write

import java.nio.file.Files
import java.nio.charset.StandardCharsets

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
      extends CustomSerializer[Meta](
        format =>
          (
            {
              case in: JValue => {
                implicit val formats = DefaultFormats ++ Seq(DateTimeSerializer)
                in.extract[Meta]
              }
            }, {
              case meta: Meta => {
                implicit val formats = DefaultFormats ++ Seq(DateTimeSerializer)
                JObject(
                  JField("created", Extraction.decompose(meta.created)),
                  JField("path", Extraction.decompose(meta.path)),
                  JField("sha1", Extraction.decompose(meta.sha1))
                )
              }
            }
        ))

  implicit val formats = DefaultFormats ++ Seq(DateTimeSerializer, MetaSerializer)
  implicit val serialization = native.Serialization

  def load(paths: DataPaths, repository: LocalPomRepository): List[Meta] = {
    assert(
      repository == LocalPomRepository.MavenCentral ||
        repository == LocalPomRepository.UserProvided)

    val metaPath = paths.meta(repository)

    val source = scala.io.Source.fromFile(metaPath.toFile)
    val ret = source.mkString.split(nl).toList
    source.close()

    ret.filter(_ != "").map(json => parse(json).extract[Meta]).sortBy(_.created)(Descending)
  }

  def append(paths: DataPaths, meta: Meta, repository: LocalPomRepository): Unit = {
    val all = load(paths, repository)

    val metaPath = paths.meta(repository)

    val sorted = (meta :: all).sortBy(_.created)(Descending)
    val jsonPerLine =
      sorted.map(s => write(s)).mkString("", System.lineSeparator, System.lineSeparator)

    Files.delete(metaPath)
    Files.write(metaPath, jsonPerLine.getBytes(StandardCharsets.UTF_8))
    ()
  }
}
