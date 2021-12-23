package ch.epfl.scala.index
package data

import java.nio.charset.StandardCharsets
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import ch.epfl.scala.index.data.bintray.BintrayMeta
import ch.epfl.scala.index.data.bintray.BintrayProtocol
import ch.epfl.scala.index.data.bintray.BintraySearch
import ch.epfl.scala.index.data.cleanup.GithubRepoExtractor
import ch.epfl.scala.index.data.maven.PomsReader
import org.json4s.native.Serialization.write
import scaladex.core.model.Project
import scaladex.infra.storage.DataPaths
import scaladex.infra.storage.LocalPomRepository
import scaladex.infra.storage.local.LocalStorageRepo

object SubIndex extends BintrayProtocol {
  def generate(source: DataPaths, destination: DataPaths): Unit = {
    def splitRepo(in: String): Project.Reference = {
      val List(owner, repo) = in.split('/').toList
      Project.Reference.from(owner, repo)
    }

    val repos =
      slurp(Paths.get("subindex.txt"))
        .split('\n')
        .map(splitRepo)
        .toSet

    val githubRepoExtractor = new GithubRepoExtractor(source)

    val pomData =
      PomsReader
        .loadAll(source)
        .flatMap {
          case (pom, repo, sha) =>
            githubRepoExtractor
              .extract(pom)
              .filter(repos.contains)
              .map((pom, repo, sha, _))
        }

    println("== Copy GitHub ==")

    pomData.foreach {
      case (_, _, _, projectRef) =>
        def repoPath(paths: DataPaths): Path =
          paths.github.resolve(s"${projectRef.organization}/${projectRef.repository}")

        val repoSource = repoPath(source)
        if (Files.isDirectory(repoSource)) {
          copyDir(repoSource, repoPath(destination))
        }
    }

    println("== Copy Poms ==")

    pomData.foreach {
      case (_, repo, sha, _) =>
        repo match {
          case pomRepo: LocalPomRepository =>
            def shaPath(paths: DataPaths): Path =
              paths.poms(pomRepo).resolve(sha + ".pom")

            copyFile(shaPath(source), shaPath(destination))
          case _ => () // does not copy ivy sbt plugins
        }
    }

    println("== Copy Parent Poms ==")

    // copy all parent poms
    List(
      LocalPomRepository.Bintray,
      LocalPomRepository.MavenCentral,
      LocalPomRepository.UserProvided
    ).foreach { pomRepo =>
      def parentShaPath(paths: DataPaths): Path =
        paths.parentPoms(pomRepo)

      copyDir(parentShaPath(source), parentShaPath(destination))
    }

    def shasFor(forRepo: LocalPomRepository): Set[String] =
      pomData
        .filter { case (_, repo, _, _) => repo == forRepo }
        .map { case (_, _, sha, _) => sha }
        .toSet

    println("== Copy MetaData ==")

    // Bintray Meta
    val bintrayShas = shasFor(LocalPomRepository.Bintray)
    val filteredBintrayMeta =
      BintrayMeta
        .load(source)
        .filter(meta => bintrayShas.contains(meta.sha1))
        .map(bintray => write[BintraySearch](bintray))
        .mkString("\n")

    writeFile(destination.meta(LocalPomRepository.Bintray), filteredBintrayMeta)

    def copyMetas(forRepo: LocalPomRepository): Unit = {
      val shas = shasFor(forRepo)
      val filteredMeta =
        Meta
          .load(source, forRepo)
          .filter(meta => shas.contains(meta.sha1))

      Meta.write(destination, filteredMeta, forRepo)
    }

    copyMetas(LocalPomRepository.UserProvided)
    copyMetas(LocalPomRepository.MavenCentral)

    println("== Copy LiveData ==")

    val destinationStorage = new LocalStorageRepo(destination)
    val sourceStorage = new LocalStorageRepo(source)
    // live
    destinationStorage.saveAllProjectSettings(
      sourceStorage
        .getAllProjectSettings()
        .view
        .filterKeys(reference => repos.contains(reference))
        .toMap
    )

    copyFile(source.movedGithub, destination.movedGithub)
  }

  private def writeFile(to: Path, content: String): Unit =
    Files.write(to, content.getBytes(StandardCharsets.UTF_8))

  private def copyFile(from: Path, to: Path): Unit = {
    Files.createDirectories(to.getParent)
    if (!Files.exists(to)) {
      Files.copy(from, to)
    }
  }

  private def copyDir(from: Path, to: Path): Unit =
    Files.walkFileTree(
      from,
      new SimpleFileVisitor[Path] {
        override def preVisitDirectory(
            dir: Path,
            attrs: BasicFileAttributes
        ): FileVisitResult = {
          Files.createDirectories(to.resolve(from.relativize(dir)))
          FileVisitResult.CONTINUE
        }
        override def visitFile(
            file: Path,
            attrs: BasicFileAttributes
        ): FileVisitResult = {
          Files.copy(
            file,
            to.resolve(from.relativize(file)),
            StandardCopyOption.REPLACE_EXISTING
          )
          FileVisitResult.CONTINUE
        }
      }
    )
}
