package ch.epfl.scala.index
package data
package cleanup

import model._

import java.nio.file._
import spray.json._

class LicenseCleanup extends DefaultJsonProtocol {
  private val byNameSource = scala.io.Source.fromFile(
      cleanupIndexBase.resolve(Paths.get("licenses", "byName.json")).toFile
  )
  private val byName =
    byNameSource.mkString.parseJson.convertTo[Map[String, List[String]]]
  byNameSource.close()

  private val licensesFromName =
    License.all.groupBy(_.shortName).mapValues(_.head)
  private val variaNameToLicense: Map[String, License] =
    innerJoin(byName, licensesFromName)((_, _)).flatMap {
      case (_, (xs, license)) =>
        xs.map((_, license))
    }

  def apply(d: maven.MavenModel): Set[License] = {
    d.licenses.map(l => variaNameToLicense.get(l.name)).flatten.toSet
  }
}
