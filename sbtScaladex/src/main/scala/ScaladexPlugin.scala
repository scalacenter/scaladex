import sbt.Keys._
import sbt._

/**
 * Place holder plugin for setting up scripted.
 */
object ScaladexPlugin extends AutoPlugin {

  object autoImport {

    lazy val Scaladex = config("Scaladex") extend Compile
    lazy val scaladexKeywords = settingKey[Seq[String]]("list of keywords for your package")
    lazy val scaladexDownloadReadme = settingKey[Boolean]("defines if scaladex should download the readme file")
    lazy val scaladexDownloadInfo = settingKey[Boolean]("defines if scaladex should download the project info")
    lazy val scaladexDownloadContributors = settingKey[Boolean]("defines if scaladex should download the contributors lost")

    lazy val baseScaladexSettings = Seq(
      scaladexKeywords := Seq(),
      scaladexDownloadContributors := false,
      scaladexDownloadInfo := false,
      scaladexDownloadReadme := false
    ) ++
      inConfig(Scaladex)(Seq(
        publishTo := Some("Scaladex" at s"http://localhost:8080/publish?readme=${scaladexDownloadReadme.value}&info=${scaladexDownloadInfo.value}&contributors=${scaladexDownloadContributors.value}${scaladexKeywords.value.map(key => s"&keywords=$key").mkString("")}&path="),
        publishArtifact in packageBin := false,
        publishArtifact in packageDoc := false,
        publishArtifact in packageSrc := false,
        credentials += Credentials(Path.userHome / ".ivy2" / ".scaladex.credentials")

      ))
  }

  import autoImport._

  override def trigger = allRequirements

  override lazy val projectSettings = baseScaladexSettings


  publish in Scaladex := {

    println("publish in Scaladex")
    (publish in Compile).value
  }

  //	def scaladexResolver: Resolver = {

  //		val readme = scaladexDownloadReadme.value
  //		val info = scaladexDownloadInfo.value
  //		val contributors = scaladexDownloadContributors.value
  //		val keywords = scaladexKeywords.value.map(key => s"&keywords=$key").mkString("")
  //
  //		val url = "http://localhost:8080/publish"

  //		"Scaladex" at s"http://localhost:8080/publish?readme=${scaladexDownloadReadme.value}&info=${scaladexDownloadInfo.value}&contributors=${scaladexDownloadContributors.value}${scaladexKeywords.value.map(key => s"&keywords=$key").mkString("")}&path="
  //	}

}