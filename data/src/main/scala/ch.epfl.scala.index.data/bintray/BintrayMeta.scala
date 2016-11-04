package ch.epfl.scala.index
package data
package bintray

import com.github.nscala_time.time.Imports._
import model.Descending

import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write

import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets

object BintrayMeta extends BintrayProtocol {

  def path(paths: DataPaths): Path = paths.meta(LocalRepository.Bintray)

  /**
    * read all currently downloaded poms and convert them to BintraySearch object
    *
    * @param path the file path
    * @return
    */
  def load(paths: DataPaths): List[BintraySearch] = {
    val source = scala.io.Source.fromFile(path(paths).toFile)
    val ret = source.mkString.split(nl).toList
    source.close()
    ret
      .filter(_ != "")
      .map(json => parse(json).extract[BintraySearch])
      .sortBy(_.created)(Descending)
  }

  def append(paths: DataPaths, meta: BintraySearch): Unit = {
    val all = load(paths)
    Files.delete(path(paths))
    val sorted = (meta :: all).sortBy(_.created)(Descending)
    val jsonPerLine =
      sorted.map(s => write(s)).mkString("", System.lineSeparator, System.lineSeparator)
    Files.write(path(paths), jsonPerLine.getBytes(StandardCharsets.UTF_8))
    ()
  }
}