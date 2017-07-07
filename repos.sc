import ch.epfl.scala.index._
import data._
import github._

import cleanup.GithubRepoExtractor
import maven.PomsReader
import model.misc.GithubRepo

import java.nio.file.{Files, Paths, Path}
import java.nio.charset.StandardCharsets

import scala.util.Success

val paths = DataPaths(Nil)

val githubRepoExtractor = new GithubRepoExtractor(paths)

val scaladexRepos =
  PomsReader
    .loadAll(paths)
    .collect { case Success((pom, _, _)) => githubRepoExtractor(pom) }
    .flatten
    .toSet

import collection.JavaConverters._

def slurp(path: Path): List[String] = {
  Files.readAllLines(path).asScala.toList
}

def splitRepo(in: String): GithubRepo = {
  val List(owner, repo) = in.split('/').toList
  GithubRepo(owner.toLowerCase, repo.toLowerCase)
}

val awesomeRepos = slurp(Paths.get("awesome-repos")).map(splitRepo).toSet

val missing = (awesomeRepos -- scaladexRepos).toList.sorted

Files.write(
  Paths.get("awesome-missing"),
  missing.mkString(System.lineSeparator).getBytes(StandardCharsets.UTF_8)
)
