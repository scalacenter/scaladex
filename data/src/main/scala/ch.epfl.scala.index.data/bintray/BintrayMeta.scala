package ch.epfl.scala.index
package data
package bintray

import com.github.nscala_time.time.Imports._
import model.Descending

import org.json4s._
import org.json4s.native.JsonMethods._

import java.nio.file.Path

object BintrayMeta extends BintrayProtocol {

  def path(paths: DataPaths): Path = paths.meta(LocalPomRepository.Bintray)

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

}
