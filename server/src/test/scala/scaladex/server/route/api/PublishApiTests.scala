package scaladex.server.route.api

import java.nio.file.Path

import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import coursier.Dependency
import coursier.Fetch
import coursier.Module
import coursier.ModuleName
import coursier.Organization
import coursier.core.Type
import org.scalatest.BeforeAndAfterEach
import scaladex.core.model.Artifact
import scaladex.core.test.MockGithubAuth
import scaladex.core.test.Values._
import scaladex.server.route.ControllerBaseSuite
import scaladex.server.route.api.PublishApi
import scaladex.server.service.PublishProcess

class PublishApiTests extends ControllerBaseSuite with BeforeAndAfterEach {
  val publishProcess: PublishProcess = PublishProcess(dataPaths, localStorage, database)
  val publishApi = new PublishApi(dataPaths, database, localStorage, githubAuth)

  val sonatype: BasicHttpCredentials = BasicHttpCredentials("token", MockGithubAuth.Sonatype.token)
  val admin: BasicHttpCredentials = BasicHttpCredentials("token", MockGithubAuth.Admin.token)
  val typelevel: BasicHttpCredentials = BasicHttpCredentials("token", MockGithubAuth.Typelevel.token)

  override protected def beforeEach(): Unit = database.reset()

  it("sonatype should publish any artifact") {
    val pomFile = downloadPom(Cats.`core_3:2.6.1`)
    val creationDate = Cats.`core_3:2.6.1`.releaseDate.get.getEpochSecond
    val entity = HttpEntity.fromPath(ContentTypes.`application/octet-stream`, pomFile)
    val request = Put(s"/publish?created=$creationDate&path=$pomFile", entity)
      .addCredentials(sonatype)

    request ~> publishApi.routes ~> check {
      status shouldBe StatusCodes.Created
      for (artifacts <- database.getArtifacts(Cats.reference))
        yield artifacts should contain theSameElementsAs Seq(Cats.`core_3:2.6.1`)
    }
  }

  it("admin should publish any artifact") {
    val pomFile = downloadPom(Cats.`core_3:2.7.0`)
    val creationDate = Cats.`core_3:2.7.0`.releaseDate.get.getEpochSecond
    val entity = HttpEntity.fromPath(ContentTypes.`application/octet-stream`, pomFile)
    val request = Put(s"/publish?created=$creationDate&path=$pomFile", entity)
      .addCredentials(admin)

    request ~> publishApi.routes ~> check {
      status shouldBe StatusCodes.Created
      for (artifacts <- database.getArtifacts(Cats.reference))
        yield artifacts should contain theSameElementsAs Seq(Cats.`core_3:2.7.0`)
    }
  }

  it("owner should publish artifact of its project") {
    val pomFile = downloadPom(Cats.core_sjs1_3)
    val creationDate = Cats.core_sjs1_3.releaseDate.get.getEpochSecond
    val entity = HttpEntity.fromPath(ContentTypes.`application/octet-stream`, pomFile)
    val request = Put(s"/publish?created=$creationDate&path=$pomFile", entity)
      .addCredentials(typelevel)

    request ~> publishApi.routes ~> check {
      status shouldBe StatusCodes.Created
      for (artifacts <- database.getArtifacts(Cats.reference))
        yield artifacts should contain theSameElementsAs Seq(Cats.core_sjs1_3)
    }
  }

  it("user should not publish artifcat of project it does not own") {
    val pomFile = downloadPom(Scalafix.artifact)
    val creationDate = Scalafix.artifact.releaseDate.get.getEpochSecond
    val entity = HttpEntity.fromPath(ContentTypes.`application/octet-stream`, pomFile)
    val request = Put(s"/publish?created=$creationDate&path=$pomFile", entity)
      .addCredentials(typelevel)

    request ~> publishApi.routes ~> check {
      // status shouldBe StatusCodes.Forbidden
      for (artifacts <- database.getArtifacts(Scalafix.reference))
        yield artifacts shouldBe empty
    }
  }

  def downloadPom(artifact: Artifact): Path = {
    val dep =
      Dependency(
        Module(Organization(artifact.groupId.value), ModuleName(artifact.artifactId)),
        artifact.version.toString
      )
        .withPublication(
          artifact.artifactId,
          Type.pom
        ) // needed to download the pom file (see https://github.com/coursier/coursier/issues/2029#issuecomment-1012033951)

    Fetch()
      .withArtifactTypes(Set(Type.pom))
      .withDependencies(Seq(dep))
      .run()
      .head
      .toPath
  }
}
