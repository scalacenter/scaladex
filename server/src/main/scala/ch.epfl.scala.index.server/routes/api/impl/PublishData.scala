package ch.epfl.scala.index
package server
package routes
package api
package impl

import data.bintray._
import data.github._

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.io.File

/**
  * Publish data model / Settings
  * @param path the file name send to scaladex
  * @param data the file content
  * @param credentials the credentials (username & password)
  * @param downloadInfo flag for downloading info
  * @param downloadContributors flag for downloading contributors
  * @param downloadReadme flag for downloading the readme file
  * @param keywords the keywords for the project
  * @param test to try the api
  */
private[api] case class PublishData(
    path: String,
    data: String,
    credentials: GithubCredentials,
    userState: UserState,
    downloadInfo: Boolean,
    downloadContributors: Boolean,
    downloadReadme: Boolean,
    keywords: Set[String],
    test: Boolean
) {

  lazy val isPom: Boolean = path matches """.*\.pom"""
  lazy val hash           = computeSha1(data)
  lazy val tempPath       = tmpPath(hash)
  lazy val savePath       = pomPath(hash)

  /**
    * write the file content to given path
    * @param destination the given destination
    */
  private def write(destination: Path): Unit = {

    delete(destination)
    Files.write(destination, data.getBytes(StandardCharsets.UTF_8))
    ()
  }

  /**
    * delete a given file
    * @param file the file name to delete
    */
  private def delete(file: Path): Unit = {

    if (Files.exists(file)) Files.delete(file)
    ()
  }

  /**
    * write the temp file to index/bintray/tmp
    */
  def writeTemp() = write(tempPath)

  /**
    * write the pom file to /index/bintray/pom_sha1
    */
  def writePom() = write(savePath)

  /**
    * delete the temp add file
    */
  def deleteTemp() = delete(tempPath)

  /**
    * resolve the filename for a specific pom by sha1
    *
    * @param sha1 the sha1 hash of the file
    * @return
    */
  private def pomPath(sha1: String) = bintrayPomBase.resolve(s"$sha1.pom")

  /**
    * get the tmp save path for the pom file
    * @param sha1 the sha1 hash
    * @return
    */
  private def tmpPath(sha1: String) = File.createTempFile(sha1, ".pom").toPath

  /**
    * generate SHA1 hash from a given String
    * @param data the sha1 hash
    * @return
    */
  private def computeSha1(data: String): String = {

    val md = java.security.MessageDigest.getInstance("SHA-1")
    md.digest(data.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }

}
