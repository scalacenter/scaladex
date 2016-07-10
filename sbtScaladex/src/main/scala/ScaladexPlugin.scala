import sbt.Keys._
import sbt._

/**
 * Place holder plugin for setting up scripted.
 */
object ScaladexPlugin extends AutoPlugin {

  object autoImport {

    lazy val scaladex = TaskKey[Unit]("scaladex-publish", "Publishes to scaladex")
    lazy val scaladexKeywords = settingKey[Seq[String]]("list of keywords for your package")
    lazy val scaladexDownloadReadme = settingKey[Boolean]("defines if scaladex should download the readme file")
    lazy val scaladexDownloadInfo = settingKey[Boolean]("defines if scaladex should download the project info")
    lazy val scaladexDownloadContributors = settingKey[Boolean]("defines if scaladex should download the contributors lost")
  }

  import autoImport._

  lazy val baseScaladexSettings: Seq[Def.Setting[_]] = Seq(
    scaladexKeywords := List(),
    scaladexDownloadReadme := true,
    scaladexDownloadInfo := true,
    scaladexDownloadContributors := true
  )
  override lazy val projectSettings = baseScaladexSettings

  lazy val publishScaladexCommand = Command.command("publish-scaladex") { (state: State) =>

    val extracted = Project.extract(state)

    Project.runTask(
      publish in Compile,
      extracted.append(List(
        publishTo := Some(scaladexUrl(extracted)),
        publishArtifact in (Compile, packageBin) := false,
        publishArtifact in (Compile, packageDoc) := false,
        publishArtifact in (Compile, packageSrc) := false
      ), state),
      true
    )

    state
  }

  private def scaladexUrl(extracted: Extracted): Resolver = {

    val readme = extracted.get(scaladexDownloadReadme)
    val info = extracted.get(scaladexDownloadInfo)
    val contributors = extracted.get(scaladexDownloadContributors)
    val keywords = extracted.get(scaladexKeywords).map(key => s"&keywords=$key").mkString("")

    val url = "http://localhost:8080/publish"

    "Scaladex" at s"$url?readme=$readme&info=$info&contributors=$contributors$keywords&path="
  }

}