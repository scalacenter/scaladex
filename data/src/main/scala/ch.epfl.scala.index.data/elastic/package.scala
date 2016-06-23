package ch.epfl.scala.index
package data

import model._
import org.elasticsearch.common.settings.Settings
import com.sksamuel.elastic4s._
import source.Indexable
import org.json4s._
import JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}

trait ProjectProtocol {

  implicit val formats = Serialization.formats(ShortTypeHints(List(
    classOf[Milestone],
    classOf[ReleaseCandidate],
    classOf[OtherPreRelease]
  ))) ++ List(ScopeSerializer, DependencySerializer)

  implicit val serialization = native.Serialization

  implicit object ProjectAs extends HitAs[Project] {

    override def as(hit: RichSearchHit): Project = read[Project](hit.sourceAsString)
  }

  implicit object ProjectIndexable extends Indexable[Project] {

    override def json(project: Project): String = write(project)
  }

  /**
    * Scope serializer, since Scope is not a case class json4s can't handle this by default
    *
    */
  object ScopeSerializer extends CustomSerializer[Scope]( format => (
    {
      case JString(scope) => Scope(scope)
    },
    {
      case scope: Scope => JString(scope.name)
    }
  ))

  /**
    * Dependency serializer, since Dependency ist just an abstraction and not the real
    * implementation - make sure that it gets serialized in the right way.
    */
  @throws(classOf[RuntimeException])
  object DependencySerializer extends CustomSerializer[Dependency]( format => (
    {
      case json: JObject =>

        val dependencyType = (json \ "type").extract[String]
        val scope = (json \ "scope").extract[Scope]

        dependencyType match {

          case "scala" => ScalaDependency((json \ "dependency").extract[Release.Reference], Some(scope))
          case "java" => JavaDependency((json \ "dependency").extract[MavenReference], Some(scope))
          case msg => throw new RuntimeException(s"Dependency type $dependencyType is not supported in deserialize json data")
        }
    },
    {
      case dep: Dependency =>

        dep match {

          case j: JavaDependency => ("type" -> "java") ~ ("dependency" -> parse(write(j.dependency))) ~ ("scope" -> dep.scope.map(_.name).getOrElse(""))
          case s: ScalaDependency => ("type" -> "scala") ~ ("dependency" -> parse(write(s.dependency))) ~ ("scope" -> dep.scope.map(_.name).getOrElse(""))
          case depType => throw new RuntimeException(s"Dependency type $depType is not supported in serialize json data")
        }
    }
  ))
}

package object elastic extends ProjectProtocol {

  /** @see https://github.com/sksamuel/elastic4s#client for configurations */

  val maxResultWindow = 10000 // <=> max amount of projects (June 1st 2016 ~ 2500 projects)
  private val home = System.getProperty("user.home")
  val esSettings = Settings.settingsBuilder()
    .put("path.home", home + "/.esdata")
    .put("max_result_window", maxResultWindow)

  lazy val esClient = ElasticClient.local(esSettings.build)

  val indexName = "scaladex"
  val collectionName = "artifacts"
}