package ch.epfl.scala.index
package cleanup

import java.nio.file._
import spray.json._

class LicenseCleanup extends DefaultJsonProtocol {
  private val byNameSource = scala.io.Source.fromFile(
    cleanupIndexBase.resolve(Paths.get("licenses", "byName.json")).toFile
  )
  private val byName = byNameSource.mkString.parseJson.convertTo[Map[String, List[String]]]
  byNameSource.close()
  
  private def innerJoin[K, A, B](m1: Map[K, A], m2: Map[K, B]): Map[K, (A, B)] = {
    m1.flatMap{ case (k, a) => 
      m2.get(k).map(b => Map((k, (a, b)))).getOrElse(Map.empty[K, (A, B)])
    }
  }

  private val licensesFromName = License.all.groupBy(_.shortName).mapValues(_.head)
  private val variaNameToLicense: Map[String, License] =
    innerJoin(byName, licensesFromName).flatMap{ case (_, (xs, license)) =>
      xs.map((_, license))
    }

  def find(ls: List[maven.License]): Set[License] = {
    ls.map(l => variaNameToLicense.get(l.name)).flatten.toSet
  }
}