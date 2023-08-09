package scaladex.server.route.api
import scala.concurrent.duration.DurationInt

import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.scalatest.BeforeAndAfterEach
import scaladex.core.model.Env
import scaladex.core.test.MockGithubAuth
import scaladex.core.test.Values._
import scaladex.infra.CoursierResolver
import scaladex.server.route.ControllerBaseSuite
import scaladex.server.route.api.PublishApi
import scaladex.server.service.PublishProcess

class PublishApiTests extends ControllerBaseSuite with BeforeAndAfterEach {
  val pomResolver = new CoursierResolver
  val publishProcess: PublishProcess = PublishProcess(dataPaths, localStorage, database, Env.Dev)
  val publishApi = new PublishApi(githubAuth, publishProcess)

  val sonatype: BasicHttpCredentials = BasicHttpCredentials("token", MockGithubAuth.Sonatype.token)
  val admin: BasicHttpCredentials = BasicHttpCredentials("token", MockGithubAuth.Admin.token)
  val typelevel: BasicHttpCredentials = BasicHttpCredentials("token", MockGithubAuth.Typelevel.token)

  override protected def beforeEach(): Unit = database.reset()

  it("sonatype should publish any artifact") {
    implicit val customTimeout = RouteTestTimeout(2.seconds)
    val pomFile = pomResolver.resolveSync(Cats.`core_3:2.6.1`.mavenReference)
    val creationDate = Cats.`core_3:2.6.1`.releaseDate.getEpochSecond
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
    val pomFile = pomResolver.resolveSync(Cats.`core_3:2.7.0`.mavenReference)
    val creationDate = Cats.`core_3:2.7.0`.releaseDate.getEpochSecond
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
    val pomFile = pomResolver.resolveSync(Cats.`core_sjs1_3:2.6.1`.mavenReference)
    val creationDate = Cats.`core_sjs1_3:2.6.1`.releaseDate.getEpochSecond
    val entity = HttpEntity.fromPath(ContentTypes.`application/octet-stream`, pomFile)
    val request = Put(s"/publish?created=$creationDate&path=$pomFile", entity)
      .addCredentials(typelevel)

    request ~> publishApi.routes ~> check {
      status shouldBe StatusCodes.Created
      for (artifacts <- database.getArtifacts(Cats.reference))
        yield artifacts should contain theSameElementsAs Seq(Cats.`core_sjs1_3:2.6.1`)
    }
  }

  it("user should not publish artifcat of project it does not own") {
    val pomFile = pomResolver.resolveSync(Scalafix.artifact.mavenReference)
    val creationDate = Scalafix.artifact.releaseDate.getEpochSecond
    val entity = HttpEntity.fromPath(ContentTypes.`application/octet-stream`, pomFile)
    val request = Put(s"/publish?created=$creationDate&path=$pomFile", entity)
      .addCredentials(typelevel)

    request ~> publishApi.routes ~> check {
      // status shouldBe StatusCodes.Forbidden
      for (artifacts <- database.getArtifacts(Scalafix.reference))
        yield artifacts shouldBe empty
    }
  }

  it("publish sbt plugin with cross version") {
    implicit val customTimeout = RouteTestTimeout(2.minutes)
    val pomFile = pomResolver.resolveSync(SbtCrossProject.mavenReference)
    val creationDate = SbtCrossProject.creationDate.getEpochSecond
    val entity = HttpEntity.fromPath(ContentTypes.`application/octet-stream`, pomFile)
    val request = Put(s"/publish?created=$creationDate&path=$pomFile", entity).addCredentials(admin)
    request ~> publishApi.routes ~> check {
      for (artifacts <- database.getArtifacts(SbtCrossProject.reference))
        yield {
          val mavenRefs = artifacts.map(_.mavenReference)
          mavenRefs should contain theSameElementsAs Seq(SbtCrossProject.mavenReference)
        }
    }
  }
}
