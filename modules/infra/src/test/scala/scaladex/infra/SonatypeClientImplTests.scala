package scaladex.infra

import java.time.Instant

import akka.actor.ActorSystem
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.Artifact
import scaladex.core.model.Artifact._
import scaladex.core.model.SemanticVersion

class SonatypeClientImplTests extends AsyncFunSpec with Matchers {
  implicit val system: ActorSystem = ActorSystem("sonatype-client-tests")
  val sonatypeClient = new SonatypeClientImpl()
  val groupId: GroupId = GroupId("ch.epfl.scala")
  val artifactId: ArtifactId = ArtifactId.parse("sbt-scalafix_2.12_1.0").get
  val version: SemanticVersion = SemanticVersion.parse("0.9.23").get

  it(s"retrieve artifactIds for ${groupId.value}") {
    for {
      res <- sonatypeClient.getAllArtifactIds(groupId)
    } yield res should contain(artifactId)
  }

  it(s"retrieve versions for groupId: ${groupId.value}, artifactId: ${artifactId.value}") {
    for {
      res <- sonatypeClient.getAllVersions(groupId, artifactId)
    } yield res should contain(version)
  }

  it(s"retrieve versions for ru.tinkoff:typed-schema-swagger-typesafe_2.12") {
    for {
      res <- sonatypeClient.getAllVersions(
        GroupId("ru.tinkoff"),
        ArtifactId.parse("typed-schema-swagger-typesafe_2.12").get
      )
    } yield res should contain(SemanticVersion.parse("0.15.2").get)
  }

  it(s"retrieve pomfile for maven reference of sbt plugin") {
    for {
      res <- sonatypeClient.getPomFile(Artifact.MavenReference("ch.epfl.scala", "sbt-scalafix_2.12_1.0", "0.9.23"))
    } yield res.get._1.startsWith("<?xml") shouldBe true
  }

  it(s"retrieve pomfile for maven reference of jvm") {
    for {
      res <- sonatypeClient.getPomFile(Artifact.MavenReference("ch.epfl.scala", "scalafix-core_2.13", "0.9.23"))
    } yield res.get._1.startsWith("<?xml") shouldBe true
  }

  it(s"retrieve pomfile for maven reference of ScalaJs") {
    for {
      res <- sonatypeClient.getPomFile(Artifact.MavenReference("ch.epfl.scala", "bloop-config_sjs1_2.13", "1.4.11"))
    } yield res.get._1.startsWith("<?xml") shouldBe true
  }

  it(s"retrieve pomfile for maven reference of Scala Native") {
    for {
      res <- sonatypeClient.getPomFile(
        Artifact.MavenReference("ch.epfl.scala", "bloop-native-bridge-0-4_2.12", "1.3.4")
      )
    } yield res.get._1.startsWith("<?xml") shouldBe true
  }

  it(s"parse date time") {
    sonatypeClient.parseDate("Fri, 11 Sep 2020 10:43:20 GMT") shouldBe Instant.ofEpochMilli(1599821000000L)
  }
}
