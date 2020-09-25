package ch.epfl.scala.index
package data
package cleanup

import model._

import org.json4s._
import org.json4s.native.Serialization.read

class LicenseCleanup(paths: DataPaths) {
  implicit private val formats = DefaultFormats

  private val byNameSource =
    scala.io.Source.fromFile(paths.licensesByName.toFile)
  private val byName = read[Map[String, List[String]]](byNameSource.mkString)
  byNameSource.close()

  private val licensesFromName =
    License.all.groupBy(_.shortName).view.mapValues(_.head).toMap
  private val variaNameToLicense: Map[String, License] =
    innerJoin(byName, licensesFromName)((_, _)).flatMap {
      case (_, (xs, license)) =>
        xs.map((_, license))
    }

  def apply(d: maven.ReleaseModel): Set[License] = {
    d.licenses.map(l => variaNameToLicense.get(l.name)).flatten.toSet
  }
}
