import sys.process._
import ammonite.ops._

def ensureSuccess(status: Int): Unit =
  if (status == 0) ()
  else sys.error(s"Process exited with status $status")

def run(args: String*): Unit = ensureSuccess(Process(args.toList).!)
def runD(args: String*)(dir: Path): Unit = {
  println(s"Running command ${args.mkString(" ")} in working directory $dir")
  ensureSuccess(Process(args.toList, Some(dir.toIO)).!)
}
def runSlurp(args: String*): String =
  Process(args.toList).lineStream.toList.headOption.getOrElse("")

/**
  * @param dataTarball   Path of the packaged (.zip) `data` application
  * @param serverTarball Path of the packaged (.zip) `server` application
  * @param reposDir      Directory that contains the `scaladex-credentials`, `scaladex-index` and
  *                      `scaladex-contrib` sub-directories containing the git repositories
  */
@main def main(dataTarball: String, serverTarball: String, reposDir: String) = {

  // --- Update bintray credentials

  val bintrayCredentialsFolder = home / ".bintray"
  if(!exists(bintrayCredentialsFolder)) {
    mkdir(bintrayCredentialsFolder)
  }

  val scaladexHome = Path(reposDir)

  val credentialsDest = scaladexHome
  val credentialsFolder = credentialsDest / "scaladex-credentials"
  println(s"Using $credentialsFolder as credential repository")

  if(!exists(credentialsFolder)) {
    runD("git", "clone", "git@github.com:scalacenter/scaladex-credentials.git", credentialsFolder.toString)(credentialsDest)
  } else {
    runD("git", "pull", "origin", "master")(credentialsFolder)
  }

  val searchCredentialsFolder = bintrayCredentialsFolder / ".credentials2"
  if(!exists(searchCredentialsFolder)){
    cp(credentialsFolder / "search-credentials", searchCredentialsFolder)
  }

  // --- Unpack the apps

  val indexDest = scaladexHome
  val indexFolder = indexDest / "scaladex-index"
  println(s"Using $indexFolder as index repository")

  val contribDest = scaladexHome
  val contribFolder = contribDest / "scaladex-contrib"
  println(s"Using $contribFolder as contrib repository")

  val scaladex = home / "scaladex"
  if(!exists(scaladex)) mkdir(scaladex)

  unpack("server", serverTarball)
  unpack("data", dataTarball)

  def unpack(name: String, appTarball: String): Unit = {

    val releases = "releases"
    val scaladexReleases = scaladex / name / "releases"
    if(!exists(scaladexReleases)) mkdir(scaladexReleases)

    val gitDescribe = runSlurp("git", "describe", "--tags") // HACK This is expected to be run from Jenkins cwd
    val destGitDescribe = scaladexReleases / gitDescribe
    if(exists(destGitDescribe)) rm(destGitDescribe)

    mkdir(destGitDescribe)

    run("unzip", appTarball, "-d", destGitDescribe.toString)

    val current = "current"
    val currentLink = scaladex / name / current
    if(exists(currentLink)) {
      rm(currentLink)
    }

    // current -> releases/1.2.3-sha
    runD("ln", "-s", s"$releases/$gitDescribe", current)(scaladex / name) // relative link
  }

  // --- Update the index (in case the model changed)

  val dataApp = scaladex / "data" / "current" / "data" / "bin" / "data"
  val dataJvmArgs = Seq("-DELASTICSEARCH=remote", "-J-Xms1G", "-J-Xmx3G")
  run(Seq(dataApp.toString, "elastic", contribFolder.toString, indexFolder.toString) ++ dataJvmArgs: _*)

  // /usr/bin/sudo -H -u scaladex /home/scaladex/bin/jenkins_redeploy.sh
  // does the rest of the work
}
