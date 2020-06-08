package ch.epfl.scala.index
package data
package maven

import java.io.File
import java.nio.file._

import scala.util.{Success, Try}
import java.util.Properties

import ch.epfl.scala.index.data.bintray.SbtPluginsData

import org.slf4j.LoggerFactory

case class MissingParentPom(dep: maven.Dependency) extends Exception

object PomsReader {
  def path(dep: maven.Dependency) = {
    import dep._
    List(
      groupId.replaceAllLiterally(".", "/"),
      artifactId,
      version,
      artifactId + "-" + version + ".pom"
    ).mkString(File.separator)
  }

  def loadAll(
      paths: DataPaths
  ): List[Try[(ReleaseModel, LocalRepository, String)]] = {
    import LocalPomRepository._

    val centralPoms = PomsReader(MavenCentral, paths).load()
    val centralShas = centralPoms.collect { case Success((_, _, sha)) => sha }.toSet

    val bintrayPoms = PomsReader(Bintray, paths).load().filter {
      case Success((_, _, sha)) => !centralShas.contains(sha)
      case _                    => true
    }
    val bintrayShas = bintrayPoms.collect { case Success((_, _, sha)) => sha }.toSet

    val usersPoms = PomsReader(UserProvided, paths).load().filter {
      case Success((_, _, sha)) =>
        !centralShas.contains(sha) && !bintrayShas.contains(sha)
      case _ => true
    }

    val ivysDescriptors = SbtPluginsData(paths.ivysData).load()

    centralPoms ::: bintrayPoms ::: usersPoms ::: ivysDescriptors
  }

  def apply(repository: LocalPomRepository, paths: DataPaths): PomsReader = {
    new PomsReader(paths.poms(repository),
                   paths.parentPoms(repository),
                   repository)
  }

  def tmp(paths: DataPaths, path: Path): PomsReader = {
    new PomsReader(path,
                   paths.parentPoms(LocalPomRepository.MavenCentral),
                   LocalPomRepository.UserProvided)
  }
}

private[maven] class PomsReader(pomsPath: Path,
                                parentPomsPath: Path,
                                repository: LocalPomRepository) {

  private val log = LoggerFactory.getLogger(getClass)

  import org.apache.maven.model._
  import resolution._
  import io._
  import building._

  private val builder = (new DefaultModelBuilderFactory).newInstance
  private val processor = new DefaultModelProcessor
  processor.setModelReader(new DefaultModelReader)

  private val resolver = new ModelResolver {
    def addRepository(repo: Repository, replace: Boolean): Unit = ()
    def addRepository(repo: Repository): Unit = ()
    def newCopy(): resolution.ModelResolver = throw new Exception("copy")
    def resolveModel(parent: Parent): ModelSource2 = {
      resolveModel(parent.getGroupId, parent.getArtifactId, parent.getVersion)
    }
    def resolveModel(groupId: String,
                     artifactId: String,
                     version: String): ModelSource2 = {
      val dep = maven.Dependency(groupId, artifactId, version)
      val target = parentPomsPath.resolve(PomsReader.path(dep))

      if (Files.exists(target)) {
        new FileModelSource(target.toFile)
      } else {
        log.error(s"Missing parent pom of $groupId:$artifactId:$version")
        throw new MissingParentPom(dep)
      }
    }
  }

  private val jdk = new Properties
  jdk.setProperty("java.version", "1.8") // << ???
  // jdk.setProperty("scala.version", "2.11.7")
  // jdk.setProperty("scala.binary.version", "2.11")

  def loadOne(path: Path): Try[(ReleaseModel, LocalPomRepository, String)] = {
    val sha1 = path.getFileName().toString.dropRight(".pom".length)

    Try {
      val request = new DefaultModelBuildingRequest
      request
        .setModelResolver(resolver)
        .setSystemProperties(jdk)
        .setPomFile(path.toFile)

      builder.build(request).getEffectiveModel

    }.map(pom => (PomConvert(pom), repository, sha1))
  }

  def load(): List[Try[(ReleaseModel, LocalPomRepository, String)]] = {
    import scala.collection.JavaConverters._

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
