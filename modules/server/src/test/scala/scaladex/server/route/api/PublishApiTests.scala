package scaladex.server.route.api
import scala.concurrent.duration.*

import scaladex.core.model.Env
import scaladex.core.test.MockGithubAuth
import scaladex.core.test.Values.*
import scaladex.infra.CoursierResolver
import scaladex.server.route.ControllerBaseSuite
import scaladex.server.service.PublishProcess

import org.apache.pekko.http.scaladsl.model.ContentTypes
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.testkit.RouteTestTimeout
import org.scalatest.BeforeAndAfterEach

class PublishApiTests extends ControllerBaseSuite with BeforeAndAfterEach:
  val pomResolver = new CoursierResolver
  val publishProcess: PublishProcess = PublishProcess(dataPaths, localStorage, database, Env.Dev)
  val publishApi = new PublishApi(githubAuth, publishProcess)

  val sonatype: BasicHttpCredentials = BasicHttpCredentials("token", MockGithubAuth.Sonatype.token)
  val admin: BasicHttpCredentials = BasicHttpCredentials("token", MockGithubAuth.Admin.token)
  val typelevel: BasicHttpCredentials = BasicHttpCredentials("token", MockGithubAuth.Typelevel.token)

  override protected def beforeEach(): Unit = database.reset()

  it("sonatype should publish any artifact") {
    given RouteTestTimeout = RouteTestTimeout(8.seconds)
    val pomFile = pomResolver.resolveSync(Cats.`core_3:2.6.1`.reference)
    val creationDate = Cats.`core_3:2.6.1`.releaseDate.getEpochSecond
    val entity = HttpEntity.fromPath(ContentTypes.`application/octet-stream`, pomFile)
    val request = Put(s"/publish?created=$creationDate&path=$pomFile", entity)
      .addCredentials(sonatype)

    request ~> publishApi.routes ~> check {
      status shouldBe StatusCodes.Created
      database
        .getArtifact(Cats.`core_3:2.6.1`.reference)
        .map(artifact => artifact should contain(Cats.`core_3:2.6.1`))
        .unsafeToFuture()
    }
  }

  it("admin should publish any artifact") {
    val pomFile = pomResolver.resolveSync(Cats.`core_2.13:2.5.0`.reference)
    val creationDate = Cats.`core_2.13:2.5.0`.releaseDate.getEpochSecond
    val entity = HttpEntity.fromPath(ContentTypes.`application/octet-stream`, pomFile)
    val request = Put(s"/publish?created=$creationDate&path=$pomFile", entity)
      .addCredentials(admin)

    request ~> publishApi.routes ~> check {
      status shouldBe StatusCodes.Created
      database
        .getArtifact(Cats.`core_2.13:2.5.0`.reference)
        .map(artifacts => artifacts should contain(Cats.`core_2.13:2.5.0`))
        .unsafeToFuture()
    }
  }

  it("owner should publish artifact of its project") {
    val pomFile = pomResolver.resolveSync(Cats.`core_sjs1_3:2.6.1`.reference)
    val creationDate = Cats.`core_sjs1_3:2.6.1`.releaseDate.getEpochSecond
    val entity = HttpEntity.fromPath(ContentTypes.`application/octet-stream`, pomFile)
    val request = Put(s"/publish?created=$creationDate&path=$pomFile", entity)
      .addCredentials(typelevel)

    request ~> publishApi.routes ~> check {
      status shouldBe StatusCodes.Created
      database
        .getArtifact(Cats.`core_sjs1_3:2.6.1`.reference)
        .map(artifacts => artifacts should contain(Cats.`core_sjs1_3:2.6.1`))
        .unsafeToFuture()
    }
  }

  it("user should not publish artifcat of project it does not own") {
    val pomFile = pomResolver.resolveSync(Scalafix.artifact.reference)
    val creationDate = Scalafix.artifact.releaseDate.getEpochSecond
    val entity = HttpEntity.fromPath(ContentTypes.`application/octet-stream`, pomFile)
    val request = Put(s"/publish?created=$creationDate&path=$pomFile", entity)
      .addCredentials(typelevel)

    request ~> publishApi.routes ~> check {
      // status shouldBe StatusCodes.Forbidden
      database
        .getArtifact(Scalafix.artifact.reference)
        .map(artifacts => artifacts shouldBe empty)
        .unsafeToFuture()
    }
  }

  it("publish sbt plugin with cross version") {
    given RouteTestTimeout = RouteTestTimeout(2.minutes)
    val pomFile = pomResolver.resolveSync(SbtCrossProject.artifactRef)
    val creationDate = SbtCrossProject.creationDate.getEpochSecond
    val entity = HttpEntity.fromPath(ContentTypes.`application/octet-stream`, pomFile)
    val request = Put(s"/publish?created=$creationDate&path=$pomFile", entity).addCredentials(admin)
    request ~> publishApi.routes ~> check {
      database
        .getProjectArtifactRefs(SbtCrossProject.reference, stableOnly = false)
        .map(artifacts => artifacts should contain theSameElementsAs Seq(SbtCrossProject.artifactRef))
        .unsafeToFuture()
    }
  }
end PublishApiTests
