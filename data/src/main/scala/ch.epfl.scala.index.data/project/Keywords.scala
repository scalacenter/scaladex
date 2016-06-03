package ch.epfl.scala.index
package data
package project

import upickle.default.{read => uread}
import build.info.BuildInfo

import model.GithubRepo

import java.nio.file.{Paths, Files}

object Keywords {

  val keywordsFile = BuildInfo.baseDirectory.toPath.resolve(Paths.get("metadata", "keywords.json"))

  val cache: Map[GithubRepo, List[String]] = 
    uread[Map[String, List[String]]](Files.readAllLines(keywordsFile).toArray.mkString(""))
    .map{ case (key, keywords) =>
      val Array(repo, org) = key.split("/")
      (GithubRepo(repo, org), keywords)
    }

  def apply(repo: GithubRepo): List[String] = cache.get(repo).getOrElse(List())
}