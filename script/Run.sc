import sys.process._
import ammonite.ops._

object Job extends Enumeration {
  type Job = Value
  val Index, Deploy, Test = Value
}

implicit val readCiType: scopt.Read[Job.Value] = scopt.Read.reads(Job withName _)

@main def main(job: Job.Value) = {
  import Job._

  val jvmOpts =
    "-Xms1G" ::
    "-Xmx3G" ::
    "-XX:ReservedCodeCacheSize=256m" ::
    "-XX:+TieredCompilation" ::
    "-XX:+CMSClassUnloadingEnabled" ::
    "-XX:+UseConcMarkSweepGC" ::
    Nil

  val branch = Process(
    "git" :: "rev-parse" :: "--abbrev-ref" :: "HEAD" :: Nil
  ).lineStream.toList.headOption.getOrElse("")
 
  if(job == Deploy && branch != "master") sys.exit
  if(job == Test && branch == "master") sys.exit

  if(!exists(cwd / "sbt")) {
    (Process("curl" :: "-s" :: "https://raw.githubusercontent.com/paulp/sbt-extras/master/sbt" :: Nil) #> (cwd / "sbt").toIO).!
    Process("chmod" :: "a+x" :: "sbt" :: Nil).!
  }

  val credentialsDest = home / "scaladex-credentials"
  if(!exists(credentialsDest)) {
    Process(
      "git" :: "clone" :: "git@github.com:scalacenter/scaladex-credentials.git" :: Nil, 
      Some(credentialsDest.toIO)
    ).!
  } else {
    Process(
      "git" :: "pull" :: "origin" :: "master" :: Nil,
      Some(credentialsDest.toIO)
    ).!
  }

  val bintrayCredentialsFolder = home / ".bintray"
  if(!exists(bintrayCredentialsFolder)) {
    mkdir(bintrayCredentialsFolder)
  }

  val searchCredentialsFolder = bintrayCredentialsFolder / ".credentials2"
  if(!exists(searchCredentialsFolder)){
    cp(credentialsDest / "search-credentials", searchCredentialsFolder)
  }

  val publishPluginCredentialsFolder = bintrayCredentialsFolder / ".credentials"
  if(!exists(publishPluginCredentialsFolder)){
    cp(credentialsDest / "sbt-plugin-credentials", publishPluginCredentialsFolder)
  }

  Process("git" :: "submodule" :: "init" :: Nil).!
  Process("git" :: "submodule" :: "update" :: Nil).!

  if(job == Index){
    
    val contribFolder = cwd / "contrib"
    val indexFolder = cwd / "index"
    val submodules = List(contribFolder, indexFolder).map(folder => Some(folder.toIO))

    // get the latest data
    submodules.foreach{ submodule =>
      Process("git" :: "checkout" :: "master" :: Nil, submodule).!
      Process("git" :: "pull" :: "origin" :: "master" :: Nil, submodule).!
    }

    // run index
    Process(
      "./sbt" :: ";clean ;data/run all" :: Nil,
      None,
      ("JVM_OPTS", ("-DELASTICSEARCH=remote" :: jvmOpts).mkString(" "))
    ).!
    
    // publish the latest data
    submodules.foreach{ submodule =>
      Process("git" :: "add" :: "-A" :: Nil, submodule).!
      Process("git" :: "commit" :: "-m" :: "`date`" :: Nil, submodule).!
      Process("git" :: "push" :: "origin" :: "master" :: Nil, submodule).!
    }
  } else if(job == Test) {
    Process(
      "./sbt" :: ";clean ;test" :: Nil,
      None,
      ("JVM_OPTS", jvmOpts.mkString(" "))
    ).!
  } else if(job == Deploy) {
    println("Deploy")
  }
}