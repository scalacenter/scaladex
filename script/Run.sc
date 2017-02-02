import sys.process._
import ammonite.ops._
import java.io.File

object Job extends Enumeration {
  type Job = Value
  val Index, Deploy, Elastic, Test = Value
}

implicit val readCiType: scopt.Read[Job.Value] = scopt.Read.reads(Job withName _)

def run(args: String*): Unit = Process(args.toList).!
def runD(args: String*)(dir: Path): Unit = Process(args.toList, Some(dir.toIO)).!
def runSlurp(args: String*): String = Process(args.toList).lineStream.toList.headOption.getOrElse("")
def runPipe(args: String*)(file: Path) = (Process(args.toList) #> file.toIO).!
def runEnv(args: String*)(envs: (String, String)*) = {
  println(args.toList)
  println(envs)
  Process(command = args.toList, cwd = None, extraEnv = envs: _*).!
}

def sbt(commands: String*): Unit = {
  val jvmOpts =
    "-DELASTICSEARCH=remote" ::
    "-Xms1G" ::
    "-Xmx3G" ::
    Nil

  // run index
  runEnv("./sbt", ("clean" :: commands.toList).mkString(";", " ;", ""))(("JVM_OPTS", jvmOpts.mkString(" ")))
}

def datetime = {
  import java.text.SimpleDateFormat
  import java.util.Calendar
  new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(Calendar.getInstance().getTime()) 
}

def updatingRepositories(contribPath: Path, indexPath: Path)(f: () => Unit): Unit = {
  // Fetch the last data from the contrib repository
  runD("git", "checkout", "master")(contribPath)
  runD("git", "remote", "update")(contribPath)
  runD("git", "pull", "origin", "master")(contribPath)

  // run index
  f()

  // Then push the server’s data (live + sonatype notifications) on the index repository
  runD("git", "add", "-A")(indexPath)
  runD("git", "commit", "-m", '"' + datetime + '"')(indexPath)
  runD("git", "push", "origin", "master")(indexPath)
}

/**
  * @param reposDir Directory that contains the `scaladex-credentials`, `scaladex-index` and `scaladex-contrib`
  *                 sub-directories containing the git repositories
  */
@main def main(reposDir: String, fullBranchName: String, job: Job.Value) = {
  import Job._

  val chmod = "chmod"

  val branch = {
    val origin = "origin/"
    if(fullBranchName.startsWith(origin)) fullBranchName.drop(origin.length)
    else fullBranchName
  }

  println(s"job $job")
  println(s"branch $branch")
 
  if(job == Deploy && branch != "master") {
    println("Exit 1")
    sys.exit
  }

  if(job == Test && branch == "master") {
    println("Exit 2")
    sys.exit
  }

  println("OK ...")

  if(!exists(cwd / "sbt")) {
    runPipe("curl", "-s", "https://raw.githubusercontent.com/paulp/sbt-extras/master/sbt")(cwd / "sbt")
    run("chmod", "a+x", "sbt")
  }

  val bintrayCredentialsFolder = home / ".bintray"
  if(!exists(bintrayCredentialsFolder)) {
    mkdir(bintrayCredentialsFolder)
  }

  val scaladexHome = Path(reposDir)

  val credentialsDest = scaladexHome
  val credentialsFolder = credentialsDest / "scaladex-credentials"

  if(!exists(credentialsFolder)) {
    run("git", "clone", "git@github.com:scalacenter/scaladex-credentials.git", credentialsFolder.toString)
  } else {
    runD("git", "pull", "origin", "master")(credentialsFolder)
  }

  val searchCredentialsFolder = bintrayCredentialsFolder / ".credentials2"
  if(!exists(searchCredentialsFolder)){
    cp(credentialsFolder / "search-credentials", searchCredentialsFolder)
  }

  val indexDest = scaladexHome
  val indexFolder = indexDest / "scaladex-index"

  val contribDest = scaladexHome
  val contribFolder = contribDest / "scaladex-contrib"
  
  val readWritePublic = "777"

  if(job == Index){

    updatingRepositories(contribFolder, indexFolder) { () =>
      // run index
      sbt(s"data/run all $contribFolder $indexFolder")
    }
  } else if(job == Elastic) {
    updatingRepositories(contribFolder, indexFolder) { () =>
      // run index
      sbt(s"data/run elastic $contribFolder $indexFolder")
    }
  } else if(job == Test) {

    sbt("test")

  } else if(job == Deploy) {

    updatingRepositories(contribFolder, indexFolder) { () =>
      sbt(
        "server/moveUniversal",
        s"data/run elastic $contribFolder $indexFolder"
      )
    }
    
    val scaladex = home / "scaladex"
    if(!exists(scaladex)) mkdir(scaladex)

    val releases = "releases"
    val scaladexReleases = scaladex / "releases"
    if(!exists(scaladexReleases)) mkdir(scaladexReleases)

    val gitDescribe = runSlurp("git", "describe", "--tags")
    val destGitDescribe = scaladexReleases / gitDescribe
    if(exists(destGitDescribe)) rm(destGitDescribe)

    mkdir(destGitDescribe)

    val packageBin =  scaladex / "universal.zip"

    run("unzip", packageBin.toString, "-d", destGitDescribe.toString)

    val current = "current"
    val currentLink = scaladex / current
    if(exists(currentLink)) {
      rm(currentLink)
    }

    // current -> releases/1.2.3-sha
    runD("ln", "-s", s"$releases/$gitDescribe", current)(scaladex) // relative link

    // /usr/bin/sudo -H -u scaladex /home/scaladex/bin/jenkins_redeploy.sh
    // does the rest of the work
  }
}
