package scaladex.data.cleanup

import org.json4s._
import org.json4s.native.Serialization.read
import scaladex.core.model.License
import scaladex.data._
import scaladex.infra.DataPaths

class LicenseCleanup(paths: DataPaths) {
  implicit private val formats: DefaultFormats.type = DefaultFormats

  private val byNameSource =
    scala.io.Source.fromFile(paths.licensesByName.toFile)
  private val byName = read[Map[String, List[String]]](byNameSource.mkString)
  byNameSource.close()

  private val licensesFromName =
    License.all.groupBy(_.shortName).view.mapValues(_.head).toMap
  private val licenseIdToLicense: Map[String, License] =
    innerJoin(byName, licensesFromName)((_, _)).flatMap {
      case (_, (xs, license)) =>
        xs.map((_, license))
    }

  def apply(d: maven.ArtifactModel): Set[License] =
    d.licenses.map(l => licenseIdToLicense.get(l.name)).flatten.toSet

  def licenseFrom(id: String): Option[License] =
    licenseIdToLicense.get(id)
}
