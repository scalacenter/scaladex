package ch.epfl.scala.index
package data
package project

import org.json4s._
import org.json4s.native.Serialization.read
import build.info.BuildInfo
import java.nio.file.{Files, Paths}

import ch.epfl.scala.index.model.misc.GithubRepo

object Keywords {
  implicit val formats = DefaultFormats
  implicit val serialization = native.Serialization

  val keywordsFile = BuildInfo.baseDirectory.toPath.resolve(Paths.get("metadata", "keywords.json"))

  val cache: Map[GithubRepo, List[String]] = 
    read[Map[String, List[String]]](Files.readAllLines(keywordsFile).toArray.mkString(""))
    .map{ case (key, keywords) =>
      val Array(repo, org) = key.split("/")
      (GithubRepo(repo, org), keywords)
    }

  def apply(repo: GithubRepo): List[String] = cache.get(repo).getOrElse(List())
}