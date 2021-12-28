package scaladex.data
package bintray

import java.nio.file.Path

import com.github.nscala_time.time.Imports._
import org.json4s._
import org.typelevel.jawn.support.json4s.Parser
import scaladex.core.util.Ordering.Descending
import scaladex.infra.storage.DataPaths
import scaladex.infra.storage.LocalPomRepository

object BintrayMeta extends BintrayProtocol {

  def path(paths: DataPaths): Path = paths.meta(LocalPomRepository.Bintray)

  /**
   * read all currently downloaded poms and convert them to BintraySearch object
   *
   * @param paths the file path
   * @return
   */
  def load(paths: DataPaths): List[BintraySearch] = {
    val source = scala.io.Source.fromFile(path(paths).toFile)
    val ret = source.mkString.split('\n').toList
    source.close()
    ret
      .filter(_ != "")
      .map(json => Parser.parseUnsafe(json).extract[BintraySearch])
      .sortBy(_.created)(Descending)
  }

}
