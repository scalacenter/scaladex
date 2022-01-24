package scaladex.data
package maven

import java.io.File
import java.nio.file._
import java.util.Properties

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.Try

import com.typesafe.scalalogging.LazyLogging
import org.apache.maven.model
import org.apache.maven.model.Parent
import org.apache.maven.model.building.DefaultModelBuilderFactory
import org.apache.maven.model.building.DefaultModelBuildingRequest
import org.apache.maven.model.building.DefaultModelProcessor
import org.apache.maven.model.building.FileModelSource
import org.apache.maven.model.building.ModelSource2
import org.apache.maven.model.io.DefaultModelReader
import org.apache.maven.model.resolution.ModelResolver
import scaladex.core.model.data.LocalPomRepository
import scaladex.core.model.data.LocalPomRepository.Bintray
import scaladex.core.model.data.LocalPomRepository.MavenCentral
import scaladex.core.model.data.LocalPomRepository.UserProvided
import scaladex.core.model.data.LocalRepository
import scaladex.core.service.PomResolver
import scaladex.data.bintray.SbtPluginsData
import scaladex.infra.storage.DataPaths

case class MissingParentPom(dep: String) extends Exception

object PomsReader {
  def path(dep: maven.Dependency): String = {
    import dep._
    List(
      groupId.replace(".", "/"),
      artifactId,
      version,
      artifactId + "-" + version + ".pom"
    ).mkString(File.separator)
  }
}

class PomsReader(resolver: PomResolver) extends LazyLogging {
  private val builder = (new DefaultModelBuilderFactory).newInstance
  private val processor = new DefaultModelProcessor
  processor.setModelReader(new DefaultModelReader)

  private val modelResolver = new ModelResolver {
    override def addRepository(repo: model.Repository, replace: Boolean): Unit = ()
    override def addRepository(repo: model.Repository): Unit = ()
    override def newCopy(): ModelResolver = throw new Exception("copy")
    override def resolveModel(parent: Parent): ModelSource2 =
      resolveModel(parent.getGroupId, parent.getArtifactId, parent.getVersion)
    override def resolveModel(
        groupId: String,
        artifactId: String,
        version: String
    ): ModelSource2 = {
      val future = resolver.resolve(groupId, artifactId, version)
      // await result from coursier
      // could block if we are short on threads
      // unblock after 3 seconds
      val pom = Await
        .result(future, 3.seconds)
        .getOrElse {
          logger.warn(s"Missing parent pom: $groupId:$artifactId:$version")
          throw new MissingParentPom(s"$groupId:$artifactId:$version")
        }

      new FileModelSource(pom.toFile)
    }
  }

  // TODO: Try to remove
  private val jdk = new Properties
  jdk.setProperty("java.version", "1.8") // << ???

  def loadAll(paths: DataPaths): Iterator[(ArtifactModel, LocalRepository, String)] = {
    val ivysDescriptors = SbtPluginsData(paths.ivysData).iterator
    loadAll(paths, MavenCentral) ++
      loadAll(paths, Bintray) ++
      loadAll(paths, UserProvided) ++
      ivysDescriptors
  }

  private def loadAll(
      paths: DataPaths,
      repository: LocalPomRepository
  ): Iterator[(ArtifactModel, LocalRepository, String)] =
    loadAll(paths.poms(repository))
      .map { case (model, sha1) => (model, repository, sha1) }

  def loadAll(directory: Path): Iterator[(ArtifactModel, String)] =
    Files
      .newDirectoryStream(directory)
      .iterator
      .asScala
      .flatMap(p => loadOne(p).toOption)

  def loadOne(path: Path): Try[(ArtifactModel, String)] = {
    val sha1 = path.getFileName().toString.dropRight(".pom".length)

    Try {
      val request = (new DefaultModelBuildingRequest)
        .setModelResolver(modelResolver)
        .setSystemProperties(jdk)
        .setPomFile(path.toFile)

      builder.build(request).getEffectiveModel
    }.map(pom => (PomConvert(pom), sha1))
  }
}
