package scaladex.infra

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.StringReader
import java.util.Properties
import java.util.zip.GZIPInputStream

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.control.NonFatal

import scaladex.core.model.IndexCursor
import scaladex.core.service.MavenCentralIndexClient
import scaladex.core.service.MavenCentralIndexClient.Record
import scaladex.core.service.MavenCentralIndexClient.Result

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller
import org.apache.pekko.util.ByteString

/** Reads the Maven Central nexus index. The chunk binary format (`doc/dev/maven-central-discovery.md` §2.5.2) is
  * trivial enough to parse without `maven-indexer` / Lucene.
  */
class MavenCentralIndexClientImpl(httpClient: CommonAkkaHttpClient)(using system: ActorSystem)
    extends MavenCentralIndexClient
    with LazyLogging:
  private given ExecutionContextExecutor = system.dispatcher
  private val baseUris = Seq(
    "https://repo1.maven.org/maven2/.index",
    "https://repo.maven.apache.org/maven2/.index"
  )
  private val filePrefix = "nexus-maven-repository-index"

  def fetchRemoteCursor(): Future[IndexCursor] =
    def loop(remaining: List[String], failures: List[String]): Future[IndexCursor] = remaining match
      case Nil =>
        Future.failed(
          new RuntimeException(s"Unable to read Maven Central index properties: ${failures.reverse.mkString("; ")}")
        )
      case baseUri :: rest =>
        val uri = s"$baseUri/$filePrefix.properties"
        httpClient
          .queueRequest(HttpRequest(uri = uri))
          .flatMap: response =>
            if response.status != StatusCodes.OK then
              response.discardEntityBytes()
              Future.failed(new RuntimeException(s"$uri returned ${response.status}"))
            else
              Unmarshaller
                .stringUnmarshaller(response.entity)
                .map(MavenCentralIndexClientImpl.parseCursor)
          .recoverWith:
            case NonFatal(e) =>
              logger.warn(s"Failed to read Maven index cursor from $uri: ${e.getMessage}")
              loop(rest, s"$uri: ${e.getMessage}" :: failures)
    loop(baseUris.toList, Nil)
  end fetchRemoteCursor

  def recordsSince(from: IndexCursor, to: IndexCursor, maxChunks: Int)(
      keep: Record => Boolean
  ): Future[Result] =
    val chunks = ((from.lastIncremental + 1) to to.lastIncremental).take(maxChunks).toList
    // stop at the first chunk that fails so the caller's cursor never advances past unread records
    def loop(remaining: List[Int], acc: Vector[Record], reached: Int): Future[Result] = remaining match
      case Nil => Future.successful(Result(acc, reached))
      case n :: rest =>
        fetchChunk(n, keep).transformWith:
          case Success(records) => loop(rest, acc ++ records, n)
          case Failure(NonFatal(e)) =>
            logger.warn(s"Stopping index scan at chunk $n (cursor stays at $reached): ${e.getMessage}")
            Future.successful(Result(acc, reached))
          case Failure(e) => Future.failed(e)
    loop(chunks, Vector.empty, from.lastIncremental)
  end recordsSince

  private def fetchChunk(n: Int, keep: Record => Boolean): Future[Seq[Record]] =
    def loop(remaining: List[String], failures: List[String]): Future[Seq[Record]] = remaining match
      case Nil =>
        Future.failed(new RuntimeException(s"Unable to read Maven index chunk $n: ${failures.reverse.mkString("; ")}"))
      case baseUri :: rest =>
        val uri = s"$baseUri/$filePrefix.$n.gz"
        httpClient
          .queueRequest(HttpRequest(uri = uri))
          .flatMap: response =>
            if response.status != StatusCodes.OK then
              response.discardEntityBytes()
              Future.failed(new RuntimeException(s"$uri returned ${response.status}"))
            else
              response.entity
                .withoutSizeLimit()
                .dataBytes
                .runFold(ByteString.empty)(_ ++ _)
                .map(bytes => MavenCentralIndexParser.parseChunk(bytes.toArray, keep))
          .recoverWith:
            case NonFatal(e) =>
              logger.warn(s"Failed to read Maven index chunk $n from $uri: ${e.getMessage}")
              loop(rest, s"$uri: ${e.getMessage}" :: failures)
    loop(baseUris.toList, Nil)
  end fetchChunk

end MavenCentralIndexClientImpl

object MavenCentralIndexClientImpl:
  private[infra] def parseCursor(body: String): IndexCursor =
    val props = new Properties()
    props.load(new StringReader(body))
    val chainId = Option(props.getProperty("nexus.index.chain-id"))
      .filter(_.nonEmpty)
      .getOrElse(throw new IllegalArgumentException("missing nexus.index.chain-id"))
    val lastIncremental = Option(props.getProperty("nexus.index.last-incremental"))
      .flatMap(_.toIntOption)
      .getOrElse(throw new IllegalArgumentException("missing or invalid nexus.index.last-incremental"))
    IndexCursor(chainId, lastIncremental)
  end parseCursor
end MavenCentralIndexClientImpl

/** Parser for one `nexus-maven-repository-index.<n>.gz` chunk. The binary framing (`doc/dev/maven-central-discovery.md`
  * §2.5.2): after gunzip, a `byte` version and `long` timestamp, then repeated records of `int fieldCount` followed by
  * `{byte flags, modified-UTF-8 name, int len, value bytes}`. We only care about the `u` (add) and `del` (remove)
  * fields, both `groupId|artifactId|version|classifier[|ext]`.
  */
object MavenCentralIndexParser:
  def parseChunk(
      gzBytes: Array[Byte],
      keep: MavenCentralIndexClient.Record => Boolean
  ): Seq[MavenCentralIndexClient.Record] =
    val in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(gzBytes), 8192))
    try
      in.readByte() // format version (always 1)
      in.readLong() // chunk timestamp
      val out = Vector.newBuilder[MavenCentralIndexClient.Record]
      var continue = true
      while continue do
        // EOF here is the legitimate end of the chunk; EOF anywhere deeper means a truncated
        // download and is left to propagate so the caller retries the chunk instead of losing records
        val fieldCount =
          try in.readInt()
          catch case _: EOFException => -1
        if fieldCount < 0 then continue = false
        else
          var uinfo: String = null
          var deleted = false
          var i = 0
          while i < fieldCount do
            in.readByte() // flags, ignored
            in.readUTF() match // field name
              case "u" => uinfo = readValue(in)
              case "del" =>
                uinfo = readValue(in)
                deleted = true
              case _ => readValue(in)
            i += 1
          if uinfo != null then
            uinfo.split('|') match
              case Array(g, a, v, _*) =>
                val record = MavenCentralIndexClient.Record(g, a, v, deleted)
                if keep(record) then out += record
              case _ => ()
        end if
      end while
      out.result()
    finally in.close()
    end try
  end parseChunk

  private def readValue(in: DataInputStream): String =
    val len = in.readInt()
    val bytes = new Array[Byte](len)
    in.readFully(bytes)
    new String(bytes, "UTF-8")
end MavenCentralIndexParser
