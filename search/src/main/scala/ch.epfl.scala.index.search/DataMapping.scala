package ch.epfl.scala.index.search

import com.sksamuel.elastic4s.ElasticDsl
import com.sksamuel.elastic4s.analyzers._
import com.sksamuel.elastic4s.mappings.FieldDefinition

object DataMapping extends ElasticDsl {
  private val urlStrip = PatternReplaceCharFilter(
    "url_strip",
    "https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)",
    ""
  )
  private val codeStrip = PatternReplaceCharFilter(
    "code_strip",
    "<code>[\\w\\W]*?<\\/code>",
    ""
  )
  private val englishStop = StopTokenFilter(
    "english_stop",
    language = Some(NamedStopTokenFilter.English)
  )
  private val englishStemmer = StemmerTokenFilter("english_stemmer", "english")
  private val englishPossessiveStemmer = StemmerTokenFilter(
    "english_possessive_stemmer",
    "possessive_english"
  )

  val englishReadme: CustomAnalyzerDefinition =
    CustomAnalyzerDefinition(
      "english_readme",
      StandardTokenizer,
      codeStrip,
      HtmlStripCharFilter,
      urlStrip,
      LowercaseTokenFilter,
      englishPossessiveStemmer,
      englishStop,
      englishStemmer
    )

  val lowercase: NormalizerDefinition =
    customNormalizer("lowercase", LowercaseTokenFilter)

  val projectFields: Seq[FieldDefinition] = List(
    textField("organization")
      .analyzer("standard")
      .fields(
        keywordField("keyword").normalizer("lowercase")
      ),
    textField("repository")
      .analyzer("standard")
      .fields(
        keywordField("keyword").normalizer("lowercase")
      ),
    textField("primaryTopic")
      .analyzer("english")
      .fields(
        keywordField("keyword").normalizer("lowercase")
      ),
    keywordField("defaultArtifact").index(false),
    keywordField("artifacts").normalizer("lowercase"),
    keywordField("customScalaDoc").index(false),
    keywordField("artifactDeprecations").index(false),
    keywordField("cliArtifacts").index(false),
    keywordField("targets"),
    keywordField("dependencies"),
    objectField("github").fields(
      textField("topics")
        .analyzer("standard")
        .fields(
          keywordField("keyword").normalizer("lowercase")
        ),
      nestedField("beginnerIssues"),
      textField("description").analyzer("english"),
      textField("readme").analyzer("english_readme")
    ),
    dateField("created"),
    dateField("updated")
  )

  val releasesFields: Seq[FieldDefinition] = Seq(
    nestedField("reference")
      .fields(
        Seq(
          keywordField("organization") normalizer "lowercase",
          keywordField("repository") normalizer "lowercase",
          keywordField("artifact") normalizer "lowercase"
        )
      )
      .includeInAll(true),
    nestedField("maven").fields(
      keywordField("groupId") normalizer "lowercase",
      keywordField("artifactId") normalizer "lowercase",
      keywordField("version")
    ),
    keywordField("version"),
    keywordField("targetType") normalizer "lowercase",
    keywordField("scalaVersion"),
    keywordField("scalaJsVersion"),
    keywordField("scalaNativeVersion"),
    keywordField("sbtVersion"),
    dateField("released")
  )

  private val releaseRefFields = Seq(
    keywordField("organization").normalizer("lowercase"),
    keywordField("repository").normalizer("lowercase"),
    keywordField("artifact").normalizer("lowercase"),
    keywordField("version"),
    keywordField("target")
  )

  val dependenciesFields: Seq[FieldDefinition] = Seq(
    objectField("dependent").fields(releaseRefFields),
    objectField("target").fields(releaseRefFields),
    keywordField("scope")
  )
}
