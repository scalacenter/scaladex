//> using scala 3.3.8
//> using dep org.jsoup:jsoup:1.22.2
//> using dep org.scala-lang.modules:scala-xml_3:2.2.0

/** Download POMs from Maven Central and publish them to Scaladex `/publish`.
  *
  * Same idea as the admin "find-missing-artifacts" task: list Scala artifacts for a group ID (optionally filtered),
  * fetch each POM, and PUT it to the production publish API.
  *
  * Auth is a GitHub PAT with `read:org`. The user must be in the scalacenter org, the sonatype org, or own the GitHub
  * repo in the POM's scm.
  *
  * Usage: export GITHUB_TOKEN=ghp_... ./scripts/PublishMissingArtifacts.scala org.typelevel
  * ./scripts/PublishMissingArtifacts.scala org.typelevel cats-core ./scripts/PublishMissingArtifacts.scala
  * org.typelevel cats-core_3 --dry-run
  */

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

import scala.jdk.CollectionConverters.*
import scala.util.Try
import scala.util.control.NonFatal
import scala.xml.XML

import org.jsoup.Jsoup

val MavenBase = "https://repo1.maven.org/maven2"
val DefaultHost = "https://index.scala-lang.org"
val UserAgent = "scaladex-publish-missing-artifacts/1.0 (+https://github.com/scalacenter/scaladex)"

// Approximate BinaryVersion.Parser + isValid + isScala from scaladex.core
val ScalaArtifactSuffix =
  raw"(?:_sjs(?:0\.6|1)|_native(?:0\.[345])|_mill\d+(?:\.\d+)?|_sbt[\w.\-]+)?_(?:2\.(?:10|11|12|13)|3)(?:_(?:0\.13|1\.0))?$$".r
val Sbt013Suffix = raw"^(.*)_2\.(?:10|11|12|13)_0\.13$$".r

case class Config(
    groupId: String,
    artifactId: Option[String],
    host: String,
    token: Option[String],
    delayMs: Long,
    dryRun: Boolean,
    limit: Option[Int]
)

val client = HttpClient
  .newBuilder()
  .followRedirects(HttpClient.Redirect.NORMAL)
  .connectTimeout(Duration.ofSeconds(30))
  .build()

@main def main(args: String*): Unit =

  val config = parseArgs(args.toList)
  if !config.dryRun && config.token.isEmpty then
    Console.err.println("Set GITHUB_TOKEN or pass --token (GitHub PAT with read:org).")
    sys.exit(2)

  val groupPath = config.groupId.replace('.', '/')
  val groupUrl = s"$MavenBase/$groupPath/"
  log(s"Listing artifacts at $groupUrl")

  val artifactIds = listDirectories(groupUrl).filter(id => isScalaArtifact(id) && matchesFilter(id, config.artifactId))
  val label = config.artifactId.fold(config.groupId)(id => s"${config.groupId}:$id")
  log(s"Found ${artifactIds.size} Scala artifact(s) for $label")

  var published = 0
  var failed = 0
  var skipped = 0
  var attempts = 0
  var stop = false
  for artifactId <- artifactIds if !stop do
    val versions = listVersions(groupPath, artifactId)
    log(s"${config.groupId}:$artifactId: ${versions.size} version(s)")
    for version <- versions if !stop do
      if config.limit.exists(attempts >= _) then
        log(s"Reached --limit ${config.limit.get}")
        stop = true
      else
        attempts += 1
        downloadPom(groupPath, artifactId, version) match
          case None =>
            log(s"  skip $artifactId:$version: POM not found")
            skipped += 1
          case Some((pom, created)) if config.dryRun =>
            log(s"  dry-run $artifactId:$version ($created)")
            published += 1
          case Some((pom, created)) =>
            Thread.sleep(config.delayMs)
            val (status, message) = publishPom(config, groupPath, artifactId, version, pom, created)
            if status == 201 then
              log(s"  published $artifactId:$version")
              published += 1
            else
              log(s"  failed $artifactId:$version: HTTP $status $message")
              failed += 1
        end match
    end for
  end for

  log(s"Done: published=$published failed=$failed skipped=$skipped")
  if failed > 0 then sys.exit(1)
end main

def parseArgs(args: List[String]): Config =
  def loop(
      rest: List[String],
      positionals: List[String],
      host: String,
      token: Option[String],
      delayMs: Long,
      dryRun: Boolean,
      limit: Option[Int]
  ): Config =
    rest match
      case Nil =>
        positionals match
          case groupId :: artifactId :: Nil =>
            Config(groupId, Some(artifactId), host, token, delayMs, dryRun, limit)
          case groupId :: Nil =>
            Config(groupId, None, host, token, delayMs, dryRun, limit)
          case _ =>
            printUsage()
            sys.exit(2)
      case "--help" :: _ | "-h" :: _ =>
        printUsage()
        sys.exit(0)
      case "--dry-run" :: tail =>
        loop(tail, positionals, host, token, delayMs, dryRun = true, limit)
      case "--host" :: value :: tail =>
        loop(tail, positionals, value, token, delayMs, dryRun, limit)
      case s"--host=$value" :: tail =>
        loop(tail, positionals, value, token, delayMs, dryRun, limit)
      case "--token" :: value :: tail =>
        loop(tail, positionals, host, Some(value), delayMs, dryRun, limit)
      case s"--token=$value" :: tail =>
        loop(tail, positionals, host, Some(value), delayMs, dryRun, limit)
      case "--delay" :: value :: tail =>
        loop(tail, positionals, host, token, value.toLong, dryRun, limit)
      case s"--delay=$value" :: tail =>
        loop(tail, positionals, host, token, value.toLong, dryRun, limit)
      case "--limit" :: value :: tail =>
        loop(tail, positionals, host, token, delayMs, dryRun, Some(value.toInt))
      case s"--limit=$value" :: tail =>
        loop(tail, positionals, host, token, delayMs, dryRun, Some(value.toInt))
      case flag :: _ if flag.startsWith("-") =>
        Console.err.println(s"Unknown option: $flag")
        printUsage()
        sys.exit(2)
      case positional :: tail =>
        loop(tail, positionals :+ positional, host, token, delayMs, dryRun, limit)
  end loop

  loop(
    args,
    Nil,
    sys.env.getOrElse("SCALADEX_URL", DefaultHost),
    sys.env.get("GITHUB_TOKEN"),
    delayMs = 100L,
    dryRun = false,
    limit = None
  )
end parseArgs

def printUsage(): Unit =
  Console.err.println(
    """Usage: PublishMissingArtifacts.scala GROUP_ID [ARTIFACT] [options]
      |
      |  GROUP_ID   Maven groupId, e.g. org.typelevel
      |  ARTIFACT   Optional Maven artifactId (cats-core_3) or artifact name (cats-core)
      |
      |Options:
      |  --host URL     Scaladex base URL (default: https://index.scala-lang.org or $SCALADEX_URL)
      |  --token TOKEN  GitHub PAT with read:org (default: $GITHUB_TOKEN)
      |  --delay MS     Delay between publish requests (default: 100)
      |  --limit N      Stop after N publish attempts
      |  --dry-run      List artifacts without publishing
      |  --help         Show this help
      |""".stripMargin
  )

def isScalaArtifact(artifactId: String): Boolean =
  ScalaArtifactSuffix.findFirstIn(artifactId).isDefined

def matchesFilter(artifactId: String, filter: Option[String]): Boolean =
  filter.forall(f => artifactId == f || artifactId.startsWith(s"${f}_"))

def listDirectories(url: String): Seq[String] =
  val normalized = if url.endsWith("/") then url else s"$url/"
  val (status, _, body) = send(get(normalized))
  println(
    Jsoup
      .parse(body)
  )
  if status != 200 then throw RuntimeException(s"Failed to list $normalized: HTTP $status")
  Jsoup
    .parse(body)
    .select("a")
    .asScala
    .toSeq
    .map(_.attr("href"))
    .collect {
      case href if href.nonEmpty => href.stripPrefix(normalized)
    }
    .filter { n =>
      val slashIdx = n.indexOf('/')
      n != "./" && n != "../" && n != "." && n != ".." && n != "/" && (slashIdx < 0 || slashIdx == n.length - 1)
    }
    .filter(_.endsWith("/"))
    .map(s => s.substring(0, s.length - 1))
    .distinct
end listDirectories

def listVersions(groupPath: String, artifactId: String): Seq[String] =
  val metaUrl = s"$MavenBase/$groupPath/$artifactId/maven-metadata.xml"
  val (status, _, body) = send(get(metaUrl))
  val fromMetadata =
    if status == 200 then versionsFromMetadata(body) else Seq.empty
  if fromMetadata.nonEmpty then fromMetadata
  else listDirectories(s"$MavenBase/$groupPath/$artifactId/")

def versionsFromMetadata(xmlText: String): Seq[String] =
  Try {
    val root = XML.loadString(xmlText)
    (root \ "versioning" \ "versions" \ "version").flatMap(_.text.trim match
      case "" => None
      case version => Some(version))
  }.recover { case NonFatal(_) => Seq.empty }.get

def pomFileNames(artifactId: String, version: String): Seq[String] =
  val standard = s"$artifactId-$version.pom"
  artifactId match
    case Sbt013Suffix(name) => Seq(standard, s"$name-$version.pom")
    case _ => Seq(standard)

def downloadPom(groupPath: String, artifactId: String, version: String): Option[(String, Instant)] =
  pomFileNames(artifactId, version).iterator
    .map { filename =>
      val url = s"$MavenBase/$groupPath/$artifactId/$version/$filename"
      val (status, headers, body) = send(get(url))
      if status == 200 then
        val created = headers
          .get("last-modified")
          .flatMap(parseDate)
          .getOrElse(Instant.now())
        Some(body -> created)
      else None
    }
    .collectFirst { case Some(result) => result }

def publishPom(
    config: Config,
    groupPath: String,
    artifactId: String,
    version: String,
    pom: String,
    created: Instant
): (Int, String) =
  val token = config.token.get
  val path = s"/$groupPath/$artifactId/$version/$artifactId-$version.pom"
  val query =
    s"path=${URLEncoder.encode(path, StandardCharsets.UTF_8)}&created=${created.getEpochSecond}"
  val url = s"${config.host.stripSuffix("/")}/publish?$query"
  val credentials = Base64.getEncoder.encodeToString(s"token:$token".getBytes(StandardCharsets.UTF_8))
  val request = HttpRequest
    .newBuilder(URI.create(url))
    .timeout(Duration.ofMinutes(2))
    .header("User-Agent", UserAgent)
    .header("Authorization", s"Basic $credentials")
    .header("Content-Type", "application/octet-stream")
    .PUT(HttpRequest.BodyPublishers.ofString(pom))
    .build()
  println("Publishing:")
  println(url)
  val (status, _, body) = send(request)
  (status, body.trim)
end publishPom

def get(url: String): HttpRequest =
  HttpRequest
    .newBuilder(URI.create(url))
    .timeout(Duration.ofMinutes(2))
    .header("User-Agent", UserAgent)
    .GET()
    .build()

def send(request: HttpRequest): (Int, Map[String, String], String) =
  val response = client.send(request, HttpResponse.BodyHandlers.ofString())
  val headers = response
    .headers()
    .map()
    .asScala
    .flatMap { case (name, values) => values.asScala.headOption.map(name.toLowerCase -> _) }
    .toMap
  (response.statusCode(), headers, response.body())

def parseDate(dateStr: String): Option[Instant] =
  Try(ZonedDateTime.parse(dateStr, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant).toOption

def log(message: String): Unit =
  Console.err.println(message)
