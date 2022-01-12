package scaladex.data
package maven

import java.io.File
import java.nio.file._
import java.util.Properties

import scala.collection.parallel.CollectionConverters._
import scala.concurrent.ExecutionContext
import scala.util.Try

import org.apache.maven.model
import org.apache.maven.model.Parent
import org.apache.maven.model.building.DefaultModelBuilderFactory
import org.apache.maven.model.building.DefaultModelBuildingRequest
import org.apache.maven.model.building.DefaultModelProcessor
import org.apache.maven.model.building.FileModelSource
import org.apache.maven.model.building.ModelSource2
import org.apache.maven.model.io.DefaultModelReader
import org.apache.maven.model.resolution.ModelResolver
import org.slf4j.LoggerFactory
import scaladex.core.service.PomResolver
import scaladex.data.bintray.SbtPluginsData
import scaladex.infra.CoursierResolver
import scaladex.infra.storage.DataPaths
import scaladex.infra.storage.LocalPomRepository
import scaladex.infra.storage.LocalPomRepository.Bintray
import scaladex.infra.storage.LocalPomRepository.MavenCentral
import scaladex.infra.storage.LocalPomRepository.UserProvided
import scaladex.infra.storage.LocalRepository

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

  def loadAll(
      paths: DataPaths
  )(implicit ec: ExecutionContext): Iterator[(ArtifactModel, LocalRepository, String)] = {

    val ivysDescriptors = SbtPluginsData(paths.ivysData).iterator
    val centralPoms = PomsReader(MavenCentral, paths).iterator
    val bintrayPoms = PomsReader(Bintray, paths).iterator
    val userPoms = PomsReader(UserProvided, paths).iterator
    centralPoms ++ bintrayPoms ++ userPoms ++ ivysDescriptors
  }

  def apply(repository: LocalPomRepository, paths: DataPaths)(implicit ec: ExecutionContext): PomsReader =
    new PomsReader(
      paths.poms(repository),
      repository,
      CoursierResolver(repository)
    )

}

private[maven] class PomsReader(
    pomsPath: Path,
    repository: LocalPomRepository,
    resolver: PomResolver
) {

  private val log = LoggerFactory.getLogger(getClass)

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
      val pom = resolver
        .resolve(groupId, artifactId, version)
        .getOrElse {
          log.error(s"Missing parent pom: $groupId:$artifactId:$version")
          throw new MissingParentPom(s"$groupId:$artifactId:$version")
        }

      new FileModelSource(pom.toFile)
    }
  }

  private val jdk = new Properties
  jdk.setProperty("java.version", "1.8") // << ???
  // jdk.setProperty("scala.version", "2.11.7")
  // jdk.setProperty("scala.binary.version", "2.11")

  def loadOne(path: Path): Try[(ArtifactModel, LocalPomRepository, String)] = {
    val sha1 = path.getFileName().toString.dropRight(".pom".length)

    Try {
      val request = (new DefaultModelBuildingRequest)
        .setModelResolver(modelResolver)
        .setSystemProperties(jdk)
        .setPomFile(path.toFile)

      builder.build(request).getEffectiveModel

    }.map(pom => (PomConvert(pom), repository, sha1))
  }

  def iterator: Iterator[(ArtifactModel, LocalPomRepository, String)] = {
    import scala.jdk.CollectionConverters._

    val stream = Files.newDirectoryStream(pomsPath).iterator
    Files.newDirectoryStream(pomsPath).iterator.asScala.flatMap(p => loadOne(p).toOption)
  }

  def load(): List[Try[(ArtifactModel, LocalPomRepository, String)]] = {
    import scala.jdk.CollectionConverters._

    val s = Files.newDirectoryStream(pomsPath)
    val rawPoms = s.asScala.toList

    val progress =
      ProgressBar(s"Reading $repository's POMs", rawPoms.size, log)
    progress.start()

    val poms = rawPoms.par.map { p =>
      progress.synchronized {
        progress.step()
      }

      loadOne(p)

    }.toList
    progress.stop()
    s.close()

    poms
  }
}
