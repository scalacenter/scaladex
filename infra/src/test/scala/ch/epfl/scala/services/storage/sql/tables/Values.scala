package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.release.MavenReference
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewProject.FormData
import ch.epfl.scala.index.newModel.NewProject.Organization
import ch.epfl.scala.index.newModel.NewProject.Repository
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName
import ch.epfl.scala.services.storage.sql.DbConf.H2
import ch.epfl.scala.services.storage.sql.SqlRepo

object Values {
  val dbConf: H2 = H2(
    "jdbc:h2:mem:scaladex_db;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
  )
  val db = new SqlRepo(dbConf)
  val project: NewProject =
    NewProject.defaultProject("scalacenter", "scaladex", None)
  val githubInfo = GithubInfo.empty
  val nonDefaultFormData: FormData = FormData(
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
    artifact = ArtifactName("play-json-extra"),
    target = None,
    description = None,
    released = None,
    resolver = None,
    licenses = Set(),
    isNonStandardLib = false
  )

}
