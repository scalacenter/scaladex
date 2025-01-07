package scaladex.infra

import java.time.Instant

import org.apache.pekko.actor.ActorSystem
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.Artifact
import scaladex.core.model.Artifact.*
import scaladex.core.model.Version

class MavenCentralClientImplTests extends AsyncFunSpec with Matchers:
  implicit val system: ActorSystem = ActorSystem("maven-central-client-tests")
  val client = new MavenCentralClientImpl()
  val groupId: GroupId = GroupId("ch.epfl.scala")
  val artifactId: ArtifactId = ArtifactId("sbt-scalafix_2.12_1.0")
  val version: Version = Version("0.9.23")

  it(s"retrieve artifactIds for ${groupId.value}") {
    for res <- client.getAllArtifactIds(groupId)
    yield res should contain(artifactId)
  }

  it(s"retrieve versions for groupId: ${groupId.value}, artifactId: ${artifactId.value}") {
    for res <- client.getAllVersions(groupId, artifactId)
    yield res should contain(version)
  }

  it(s"retrieve versions for ru.tinkoff:typed-schema-swagger-typesafe_2.12") {
    for res <- client.getAllVersions(
        GroupId("ru.tinkoff"),
        ArtifactId("typed-schema-swagger-typesafe_2.12")
      )
    yield res should contain(Version("0.15.2"))
  }

  it(s"retrieve pomfile for maven reference of sbt plugin") {
    for res <- client.getPomFile(Artifact.Reference.from("ch.epfl.scala", "sbt-scalafix_2.12_1.0", "0.9.23"))
    yield res.get._1.startsWith("<?xml") shouldBe true
  }

  it(s"retrieve pomfile for maven reference of jvm") {
    for res <- client.getPomFile(Artifact.Reference.from("ch.epfl.scala", "scalafix-core_2.13", "0.9.23"))
    yield res.get._1.startsWith("<?xml") shouldBe true
  }

  it(s"retrieve pomfile for maven reference of ScalaJs") {
    for res <- client.getPomFile(Artifact.Reference.from("ch.epfl.scala", "bloop-config_sjs1_2.13", "1.4.11"))
    yield res.get._1.startsWith("<?xml") shouldBe true
  }

  it(s"retrieve pomfile for maven reference of Scala Native") {
    for res <- client.getPomFile(Artifact.Reference.from("ch.epfl.scala", "bloop-native-bridge-0-4_2.12", "1.3.4"))
    yield res.get._1.startsWith("<?xml") shouldBe true
  }

  it(s"parse date time") {
    client.parseDate("Wed, 23 Sep 2020 11:40:44 GMT") shouldBe Instant.ofEpochSecond(1600861244L)
  }
end MavenCentralClientImplTests
