import sys.process._
import ammonite.ops._

object Job extends Enumeration {
  type Job = Value
  val Index, Elastic = Value
}

implicit val readCiType: scopt.Read[Job.Value] = scopt.Read.reads(Job withName _)

def run(args: String*): Unit = {
  val status = Process(args.toList).!
  if (status == 0) ()
  else sys.error(s"Process '${args.mkString(" ")}' exited with status $status")
}

/**
  * @param dataPath Path of the deployed `data` application
  * @param reposDir Directory that contains the `scaladex-credentials`, `scaladex-index` and
  *                 `scaladex-contrib` sub-directories containing the git repositories
  */
@main def main(dataPath: String, reposDir: String, job: Job.Value) = {
  import Job._

  println(s"job $job")

  val scaladexHome = Path(reposDir)

  val indexDest = scaladexHome
  val indexFolder = indexDest / "scaladex-index"
  println(s"Using $indexFolder as index repository")

  val contribDest = scaladexHome
  val contribFolder = contribDest / "scaladex-contrib"
  println(s"Using $contribFolder as contrib repository")

  val dataJvmArgs = Seq("-DELASTICSEARCH=remote", "-J-Xms1G", "-J-Xmx3G")

  val steps =
    job match {
      case Index   => "all"
      case Elastic => "elastic"
    }

  run(Seq(dataPath, steps, contribFolder.toString, indexFolder.toString) ++ dataJvmArgs: _*)
}
