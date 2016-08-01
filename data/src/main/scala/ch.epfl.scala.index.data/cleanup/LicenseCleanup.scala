package ch.epfl.scala.index
package data
package cleanup

import model._

import java.nio.file._

import org.json4s._
import org.json4s.native.Serialization.read

class LicenseCleanup {
  implicit private val formats       = DefaultFormats
  implicit private val serialization = native.Serialization

  private val byNameSource = scala.io.Source.fromFile(
      cleanupIndexBase.resolve(Paths.get("licensesByName.json")).toFile
  )
  private val byName = read[Map[String, List[String]]](byNameSource.mkString)
  byNameSource.close()

  private val licensesFromName = License.all.groupBy(_.shortName).mapValues(_.head)
  private val variaNameToLicense: Map[String, License] = innerJoin(byName, licensesFromName)((_, _)).flatMap {
    case (_, (xs, license)) =>
      xs.map((_, license))
  }

  def apply(d: maven.MavenModel): Set[License] = {
    d.licenses.map(l => variaNameToLicense.get(l.name)).flatten.toSet
  }
}
