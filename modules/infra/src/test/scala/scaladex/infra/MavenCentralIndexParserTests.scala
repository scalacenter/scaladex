package scaladex.infra

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.GZIPOutputStream

import scaladex.core.service.MavenCentralIndexClient.Record

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class MavenCentralIndexParserTests extends AnyFunSpec with Matchers:
  /** Writes a chunk in the maven-indexer transport format. */
  private def chunk(records: Seq[Map[String, String]]): Array[Byte] =
    val raw = new ByteArrayOutputStream()
    val gz = new GZIPOutputStream(raw)
    val out = new DataOutputStream(gz)
    out.writeByte(1) // version
    out.writeLong(0L) // timestamp
    records.foreach { fields =>
      out.writeInt(fields.size)
      fields.foreach {
        case (name, value) =>
          out.writeByte(0) // flags
          out.writeUTF(name)
          val bytes = value.getBytes("UTF-8")
          out.writeInt(bytes.length)
          out.write(bytes)
      }
    }
    out.close()
    raw.toByteArray
  end chunk

  it("parses added and deleted artifact records, skipping others") {
    val bytes = chunk(
      Seq(
        Map("u" -> "com.example|lib_3|1.0.0|NA", "i" -> "jar|123|456|1|1|1|jar", "n" -> "lib"),
        Map("u" -> "com.example|lib_sjs1_3|1.0.0|sources|jar"),
        Map("del" -> "org.old|gone_2.13|0.1.0|NA", "m" -> "123"),
        Map("allGroups" -> "allGroups", "allGroupsList" -> ""),
        Map("DESCRIPTOR" -> "NexusIndex", "IDXINFO" -> "1.0|central")
      )
    )
    val records = MavenCentralIndexParser.parseChunk(bytes, _ => true)
    records shouldBe Seq(
      Record("com.example", "lib_3", "1.0.0", deleted = false),
      Record("com.example", "lib_sjs1_3", "1.0.0", deleted = false),
      Record("org.old", "gone_2.13", "0.1.0", deleted = true)
    )
  }

  it("applies the keep predicate during the scan") {
    val bytes = chunk(
      Seq(
        Map("u" -> "com.example|lib_3|1.0.0|NA"),
        Map("u" -> "com.example|tool|1.0.0|NA")
      )
    )
    MavenCentralIndexParser.parseChunk(bytes, _.artifactId.endsWith("_3")) shouldBe
      Seq(Record("com.example", "lib_3", "1.0.0", deleted = false))
  }
end MavenCentralIndexParserTests
