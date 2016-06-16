package ch.epfl.scala.index
package data
package maven

import bintray._

import me.tongfei.progressbar._

import java.io.File
import java.nio.file._

import scala.util.{Try, Success, Failure}

import java.util.Properties

case class MissingParentPom(dep: maven.Dependency) extends Exception

/*
We have ~50 000 POMs in index/bintray
~45 000 will load

Common problems:
* 1 600 duplicate tags
* 2 200 duplicate repo id
*   700 xmlns tags in invalid places

https://github.com/sbt/sbt/issues/2566

*/
object PomsReader {
  import org.apache.maven.model._
  import resolution._
  import io._
  import building._ 

  val parentPomsBase = bintray.bintrayIndexBase.resolve("poms_parent")

  private val builder = (new DefaultModelBuilderFactory).newInstance
  private val processor = new DefaultModelProcessor
  processor.setModelReader(new DefaultModelReader)

  def path(dep: maven.Dependency) = {
    import dep._
    List(
      groupId.replaceAllLiterally(".", "/"),
      artifactId,
      version,
      artifactId + "-" + version + ".pom"
    ).mkString(File.separator)
  }

  private val resolver = new ModelResolver {
    def addRepository(repo: Repository, replace: Boolean): Unit = ()
    def addRepository(repo: Repository): Unit = ()
    def newCopy(): resolution.ModelResolver = throw new Exception("copy")   
    def resolveModel(parent: Parent): ModelSource2 = {
      resolveModel(parent.getGroupId, parent.getArtifactId, parent.getVersion)
    }
    def resolveModel(groupId: String, artifactId: String, version: String): ModelSource2 = {
      val dep = maven.Dependency(groupId, artifactId, version)
      val target = parentPomsBase.resolve(path(dep))

      if(Files.exists(target)) {
        new FileModelSource(target.toFile)
      } else throw new MissingParentPom(dep)
    }
  }

  private val jdk = new Properties
  jdk.setProperty("java.version", "1.8") // << ???
  // jdk.setProperty("scala.version", "2.11.7")
  // jdk.setProperty("scala.binary.version", "2.11")

  private def resolve(pom: Path) = {
    val request = new DefaultModelBuildingRequest
    request
      .setModelResolver(resolver)
      .setSystemProperties(jdk)
      .setPomFile(pom.toFile)

    builder.build(request).getEffectiveModel
  }

  def load(): List[Try[(MavenModel, List[BintraySearch])]]  = {
    val meta = BintrayMeta.sortedByCreated(bintrayCheckpoint).groupBy(_.sha1)

    import scala.collection.JavaConverters._

    val s = Files.newDirectoryStream(bintray.bintrayPomBase)
    val rawPoms = s.asScala.toList

    val progress = new ProgressBar("Reading POMs", rawPoms.size)
    progress.start()

    def sha1(path: Path) = path.getFileName().toString.dropRight(".pom".length)

    def keep(pom: maven.MavenModel, metas: List[BintraySearch]) = {
      val packagingOfInterest = Set("aar", "jar", "bundle")
      val typesafeNonOSS = Set(
        "for-subscribers-only",
        "instrumented-reactive-platform",
        "subscribers-early-access"
      )
      packagingOfInterest.contains(pom.packaging) &&
      !metas.exists(meta => meta.owner == "typesafe" && typesafeNonOSS.contains(meta.repo))
    }

    val poms = rawPoms
      .map{p =>
        progress.step()
        Try(resolve(p)).map(pom => (
          pom, 
          meta.get(sha1(p)).getOrElse(List())
        ))
      }
      .map{
        case Success((pom, metas)) => Success((PomConvert(pom), metas))
        case Failure(e) => Failure(e)
      }
      .filter{
        case Success((pom, metas)) => keep(pom, metas)
        case _ => true
      }
      
    progress.stop()
    s.close()

    poms
  }

  // Useful in ScalaKata to play with the data
  lazy val loadOnceToExperiment = load()
}