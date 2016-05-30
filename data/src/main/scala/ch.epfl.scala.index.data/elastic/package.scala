package ch.epfl.scala.index
package data

import model._

import upickle.default._

import org.elasticsearch.common.settings.Settings
import com.sksamuel.elastic4s._
import source.Indexable

trait ProjectProtocol {
  implicit object ProjectAs extends HitAs[Project] {
    override def as(hit: RichSearchHit): Project = {
      read[Project](hit.sourceAsString)
    }
  }

  implicit object ProjectIndexable extends Indexable[Project] {
    override def json(project: Project): String = {
      write(project)
    }
  }
}

package object elastic extends ProjectProtocol {
  private val home = System.getProperty("user.home")
  val esSettings = Settings.settingsBuilder().put("path.home", home + "/.esdata")
  lazy val esClient = ElasticClient.local(esSettings.build)

  val indexName = "scaladex"
  val collectionName = "artifacts"
}