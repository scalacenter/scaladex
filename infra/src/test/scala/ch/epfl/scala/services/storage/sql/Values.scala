package ch.epfl.scala.services.storage.sql

import scala.concurrent.ExecutionContext

import cats.effect.ContextShift
import cats.effect.IO
import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.release.MavenReference
import ch.epfl.scala.index.model.release.Scala3Version
import ch.epfl.scala.index.model.release.ScalaJvm
import ch.epfl.scala.index.model.release.ScalaVersion
import ch.epfl.scala.index.newModel.NewDependency
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewProject.DataForm
import ch.epfl.scala.index.newModel.NewProject.Organization
import ch.epfl.scala.index.newModel.NewProject.Repository
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName
import ch.epfl.scala.services.storage.sql.DbConf.PostgreSQL
import doobie.Transactor
import doobie.util.transactor

object Values {
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)
//  val dbConf: H2 = H2(
//    "jdbc:h2:mem:scaladex_db;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
//  )
  val dbConf: PostgreSQL = DbConf
    .from("jdbc:postgresql://user:password@localhost:5432/scaladex")
    .get
    .asInstanceOf[PostgreSQL]
  val xa: transactor.Transactor.Aux[IO, Unit] =
    Transactor
      .fromDriverManager[IO](dbConf.driver, dbConf.url, "user", "password")
  val db = new SqlRepo(dbConf, xa)

  val project: NewProject =
    NewProject.defaultProject("scalacenter", "scaladex", None)
  val githubInfo = GithubInfo.empty
  val nonDefaultFormData: DataForm = DataForm(
    defaultStableVersion = false,
    defaultArtifact = None,
    strictVersions = false,
    customScalaDoc = None,
    documentationLinks = List(),
    deprecated = false,
    contributorsWanted = false,
    artifactDeprecations = Set(),
    cliArtifacts = Set(),
    primaryTopic = Some("Scala3")
  )
  val projectWithGithubInfo: NewProject =
    NewProject.defaultProject(
      "scalacenter",
      "scalafix",
      Some(githubInfo),
      formData = nonDefaultFormData
    )
  val release: NewRelease = NewRelease(
    MavenReference(
      "com.github.xuwei-k",
      "play-json-extra_2.10",
      "0.1.1-play2.3-M1"
    ),
    version = SemanticVersion.tryParse("0.1.1-play2.3-M1").get,
    organization = Organization("xuwei-k"),
    repository = Repository("play-json-extra"),
    artifactName = ArtifactName("play-json-extra"),
    target = Some(ScalaJvm(ScalaVersion.`2.11`)),
    description = None,
    released = None,
    resolver = None,
    licenses = Set(),
    isNonStandardLib = false
  )
  val releaseCatsCore: NewRelease =
    releaseForCats(ArtifactName("cats-core"), "cats-core_3")
  val releaseCatsKernel: NewRelease =
    releaseForCats(ArtifactName("cats-kernel"), "cats-kernel_3")
  val dependenciesForCat: Seq[NewDependency] = Seq(
    NewDependency(
      source = releaseCatsCore.maven,
      target = MavenReference("org.typelevel", "cats-kernel_3", "2.6.1"),
      "compile"
    ),
    NewDependency(
      source = releaseCatsCore.maven,
      target = MavenReference(
        "com.gu",
        "ztmp-scala-automation_2.10",
        "1.9"
      ), // dependency with a corresponding release
      "compile"
    )
  )
  val dependency: NewDependency = NewDependency(
    source = MavenReference(
      "cats-effect",
      "cats-effect-kernel_3",
      "3.2.3"
    ),
    target = MavenReference("org.typelevel", "cats-core_3", "2.6.1"),
    "compile"
  )

  def releaseForCats(
      artifactName: ArtifactName,
      artifactId: String
  ): NewRelease =
    NewRelease(
      MavenReference(
        "org.typelevel",
        artifactId,
        "2.6.1"
      ),
      SemanticVersion.tryParse("2.6.1").get,
      organization = Organization("typelevel"),
      repository = Repository("cats"),
      artifactName = artifactName,
      target = Some(ScalaJvm(Scala3Version.`3`)),
      description = None,
      released = None,
      resolver = None,
      licenses = Set(),
      isNonStandardLib = false
    )

}
