package ch.epfl.scala.index
package data.project

import model.misc.GithubRepo

import org.json4s._
import org.json4s.native.Serialization.read
import build.info.BuildInfo
import java.nio.file.{Files, Paths}

object Keywords {
  implicit val formats       = DefaultFormats
  implicit val serialization = native.Serialization

  val keywordsFile = BuildInfo.baseDirectory.toPath.resolve(Paths.get("metadata", "keywords.json"))

  val cache: Map[GithubRepo, Set[String]] =
    read[Map[String, Set[String]]](Files.readAllLines(keywordsFile).toArray.mkString("")).map {
      case (key, keywords) =>
        val Array(repo, org) = key.split("/")
        (GithubRepo(repo, org), keywords)
    }

  def apply(repo: GithubRepo): Set[String] = cache.get(repo).getOrElse(Set())
}
