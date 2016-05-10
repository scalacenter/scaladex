package ch.epfl.scala.index
package elastic

import bintray._
import cleanup._

import me.tongfei.progressbar._

import com.sksamuel.elastic4s._
import ElasticDsl._

import mappings.FieldType._


import source.Indexable
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.Success

trait ArtifactProtocol extends DefaultJsonProtocol {
  implicit val formatArtifactRef = jsonFormat3(ArtifactRef)
  implicit val formatLicense = jsonFormat3(License.apply)
  implicit val formatISO_8601_Date = jsonFormat1(ISO_8601_Date)
  implicit val formatGithubRepo = jsonFormat2(GithubRepo)
  implicit val formatArtifact = jsonFormat8(Artifact)
  implicit val formatProject = jsonFormat3(Project)

  implicit object ProjectAs extends HitAs[Project] {
    override def as(hit: RichSearchHit): Project = {
      hit.sourceAsString.parseJson.convertTo[Project]
    }
  }

  implicit object ProjectIndexable extends Indexable[Project] {
    override def json(t: Project): String = t.toJson.compactPrint
  }
}

class SeedElasticSearch extends ArtifactProtocol {
  def run(): Unit = {
    def keep(pom: maven.MavenModel, metas: List[BintraySearch]) = {
      val packagingOfInterest = Set("aar", "jar")
      val typesafeNonOSS = Set(
        "for-subscribers-only",
        "instrumented-reactive-platform",
        "subscribers-early-access"
      )
      packagingOfInterest.contains(pom.packaging) &&
      !metas.exists(meta => meta.owner == "typesafe" && typesafeNonOSS.contains(meta.repo))
    }

    val poms = maven.Poms.get.collect{ case Success((pom, metas)) =>
      (maven.PomConvert(pom), metas) 
    }.filter{ case (pom, metas) => keep(pom, metas)}

    val scmCleanup = new ScmCleanup
    val licenseCleanup = new LicenseCleanup

    val progress = new ProgressBar("Convert POMs to Artifact", poms.size)
    progress.start()

    val artifacts = poms.map{ case (pom, metas) =>
      import pom._

      progress.step()

      Artifact(
        name,
        description,
        ArtifactRef(
          groupId,
          artifactId,
          version
        ),
        metas.map(meta => ISO_8601_Date(meta.created.toString)), // +/- 3 days offset
        metas.forall(meta => meta.owner == "bintray" && meta.repo == "jcenter"),
        dependencies.map{ dependency =>
          import dependency._
          ArtifactRef(
            groupId,
            artifactId,
            version
          )
        }.toSet,
        scmCleanup(pom),
        licenseCleanup(pom)
      )
    }

    def Desc[T : Ordering] = implicitly[Ordering[T]].reverse

    val projects = artifacts.groupBy(a => (a.ref.groupId, a.ref.artifactId)).map{ case ((gid, aid), as) =>
      Project(
        gid,
        aid,
        try { 
          as.toList.sortBy(a => SemanticVersion(a.ref.version))(Desc)
        } catch {
          case scala.util.control.NonFatal(e) => {
            println("cannot sort:")
            println(as.map(_.ref.version))
            println("====")
            throw e
          }
        }
      )
    }

    progress.stop()

    Await.result(
      esClient.execute {
        create.index(indexName).mappings(
          mapping(collectionName) fields (
            field("groupId") typed StringType index "not_analyzed",
            field("artifactId") typed StringType index "not_analyzed"
          )
        )
      },
      Duration.Inf
    )

    Await.result(
      esClient.execute {
        bulk(
          projects.map(project => 
            index into indexName / collectionName source project
          )
        )
      },
      Duration.Inf
    )

    ()
  }
}