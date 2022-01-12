import sbt._
import Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object ScalaJSHelper {
  def packageScalaJS(client: Project): Seq[Setting[_]] = Seq(
    watchSources ++= (client / watchSources).value,
    // Pick fastOpt when developing and fullOpt when publishing
    Compile / resourceGenerators += Def.task {
      val js = (client / Compile / fastOptJS).value.data
      val sourceMap = getSourceMap(js)
      IO.copy(
        Seq(
          js -> (Compile / resourceManaged).value / js.getName,
          sourceMap -> (Compile / resourceManaged).value / sourceMap.getName
        )
      ).toSeq
    }.taskValue,
    Compile / packageBin / mappings ++= {
      val optJs = (client / Compile / fullOptJS).value.data
      val sourceMap = getSourceMap(optJs)
      Seq(
        optJs -> optJs.getName,
        sourceMap -> sourceMap.getName
      )
    }
  )

  private def getSourceMap(jsFile: java.io.File): File =
    file(jsFile.getAbsolutePath + ".map")
}
