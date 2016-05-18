package ch.epfl.scala.index
package data

import org.elasticsearch.common.settings.Settings
import com.sksamuel.elastic4s._

package object elastic extends ArtifactProtocol {
  private val home = System.getProperty("user.home")
  val esSettings = Settings.settingsBuilder().put("path.home", home + "/.esdata")
  lazy val esClient = ElasticClient.local(esSettings.build)

  val indexName = "scaladex"
  val collectionName = "artifacts"
}