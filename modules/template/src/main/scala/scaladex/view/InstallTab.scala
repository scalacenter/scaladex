package scaladex.view

import play.twirl.api.Html
import play.twirl.api.StringInterpolation
import scaladex.core.model.Artifact

case class InstallTab(ref: String, title: String, install: String, description: Html)

object InstallTab {
  def allOf(artifact: Artifact, cliArtifacts: Set[Artifact.Name]): Seq[InstallTab] = {
    val coursierTab =
      if (cliArtifacts.contains(artifact.artifactName))
        Some(
          InstallTab(
            "coursier",
            "Coursier",
            artifact.csLaunch,
            html"""<a href="https://get-coursier.io/docs/cli-overview">Coursier</a>"""
          )
        )
      else None
    val sbtTab = artifact.sbtInstall.map(install => InstallTab("sbt", "sbt", install, html""))
    val millTab = InstallTab(
      "mill",
      "Mill",
      artifact.millInstall,
      html"""<a href="https://com-lihaoyi.github.io/mill">Mill build tool</a>"""
    )
    val scalaCliTab = InstallTab(
      "scala-cli",
      "Scala CLI",
      artifact.scalaCliInstall,
      html"""<a href="https://scala-cli.virtuslab.org/docs/overview">Scala CLI</a>"""
    )
    val ammoniteTab = InstallTab(
      "ammonite",
      "Ammonite",
      artifact.ammInstall,
      html"""<a href="https://ammonite.io/#Ammonite-REPL">Ammonite REPL</a>"""
    )
    val mavenTab = InstallTab("maven", "Maven", artifact.mavenInstall, html"")
    val gradleTab = InstallTab("gradle", "Gradle", artifact.gradleInstall, html"")

    coursierTab.toSeq ++ sbtTab.toSeq ++ Seq(millTab, scalaCliTab, ammoniteTab, mavenTab, gradleTab)
  }
}
