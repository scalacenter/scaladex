import sys.process._
import ammonite.ops._

object Job extends Enumeration {
  type Job = Value
  val Index, Deploy, Test = Value
}

implicit val readCiType: scopt.Read[Job.Value] = scopt.Read.reads(Job withName _)

def run(args: String*): Unit = Process(args.toList).!
def runD(args: String*)(dir: Path): Unit = Process(args.toList, Some(dir.toIO)).!
def runSlurp(args: String*): String = Process(args.toList).lineStream.toList.headOption.getOrElse("")
def runPipe(args: String*)(file: Path) = (Process(args.toList) #> file.toIO).!
def runEnv(args: String*)(envs: (String, String)*) = Process(command = args.toList, cwd = None, extraEnv = envs: _*).!

def sbt(commands: String*): Unit = {
  val jvmOpts =
    "-DELASTICSEARCH=remote" ::
    "-Xms1G" ::
    "-Xmx3G" ::
    "-XX:ReservedCodeCacheSize=256m" ::
    "-XX:+TieredCompilation" ::
    "-XX:+CMSClassUnloadingEnabled" ::
    "-XX:+UseConcMarkSweepGC" ::
    Nil

  // run index
  runEnv("./sbt", ("clean" :: commands.toList).mkString(";", " ;", ""))(("JVM_OPTS", jvmOpts.mkString(" ")))
}

def datetime = {
  import java.text.SimpleDateFormat
  import java.util.Calendar
  new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(Calendar.getInstance().getTime()) 
}

def updatingSubmodules(submodules: List[Path])(f: () => Unit): Unit = {
  // run index
  f()

  // publish the latest data
  submodules.foreach{ submodule =>
    runD("git", "add", "-A")(submodule)
    runD("git", "commit", "-m", '"' + datetime + '"' )(submodule)
    runD("git", "pull", "origin", "master")(submodule)
    runD("git", "push", "origin", "master")(submodule)
  }
}

@main def main(fullBranchName: String, job: Job.Value) = {
  import Job._

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

  val credentialsDest = home
  val credentialsFolder = credentialsDest / "scaladex-credentials"
  if(!exists(credentialsFolder)) {
    runD("git", "clone", "git@github.com:scalacenter/scaladex-credentials.git")(credentialsDest)
  } else {
    runD("git", "pull", "origin", "master")(credentialsFolder)
  }

  val bintrayCredentialsFolder = home / ".bintray"
  if(!exists(bintrayCredentialsFolder)) {
    mkdir(bintrayCredentialsFolder)
  }

  val searchCredentialsFolder = bintrayCredentialsFolder / ".credentials2"
  if(!exists(searchCredentialsFolder)){
    cp(credentialsFolder / "search-credentials", searchCredentialsFolder)
  }

  val publishPluginCredentialsFolder = bintrayCredentialsFolder / ".credentials"
  if(!exists(publishPluginCredentialsFolder)){
    cp(credentialsFolder / "sbt-plugin-credentials", publishPluginCredentialsFolder)
  }

  run("git", "submodule", "init")
  run("git", "submodule", "update")
  
  val readPublic      = "705"
  val readWritePublic = "777"

  val contribFolder = cwd / "contrib"
  val indexFolder   = cwd / "index"
  val bintrayFolder = indexFolder / "bintray"
  val githubFolder  = indexFolder / "github"
  val chmod = "chmod"
  
  run(chmod, readPublic, (home / "build").toString)
  run(chmod, readPublic, (contribFolder / "claims.json").toString)
  run(chmod, readWritePublic, (bintrayFolder / "poms_sha").toString)
  run(chmod, readWritePublic, (bintrayFolder / "poms_parent").toString)
  run(chmod, readWritePublic, "-R", githubFolder.toString)

  if(job == Index){

    updatingSubmodules(List(contribFolder, indexFolder)){ () =>
      // run index
      sbt("data/run all")
    }

    // make shure indexed projects are accessible
    runD(chmod, "-R", readWritePublic)(githubFolder)

  } else if(job == Test) {

    sbt("test")

  } else if(job == Deploy) {

    updatingSubmodules(List(indexFolder)){ () =>
      sbt(
        // "data/run live",
        // "data/run elastic",
        "server/universal:packageBin"
      )
    }
    
    val scaladex = home / "scaladex"
    if(!exists(scaladex)) mkdir(scaladex)

    val scaladexReleases = scaladex / "releases"
    if(!exists(scaladexReleases)) mkdir(scaladexReleases)

    val gitDescribe = runSlurp("git", "describe", "--tags")
    val destGitDescribe = scaladexReleases / gitDescribe
    if(exists(destGitDescribe)) rm(destGitDescribe)

    mkdir(destGitDescribe)

    val packageBin = cwd / "server" / "target" / "universal" / "scaladex.zip"

    run("unzip", packageBin.toString, "-d", destGitDescribe.toString)

    val current = "current"
    val currentLink = scaladex / current
    if(exists(currentLink)) {
      rm(currentLink)
    }

    // /scaladex/current -> /scaladex/releases/1.2.3-sha
    runD("ln", "-s", destGitDescribe.toString, current)(scaladex)

    // /usr/bin/sudo -H -u scaladex /home/scaladex/bin/jenkins_redeploy.sh
    // does the rest of the work
  }
}