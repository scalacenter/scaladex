package ch.epfl.scala.index.search

import com.sksamuel.elastic4s.ElasticDsl
import com.sksamuel.elastic4s.requests.mappings.FieldDefinition
import com.sksamuel.elastic4s.analysis._
import com.sksamuel.elastic4s.requests.analyzers.HtmlStripCharFilter
import com.sksamuel.elastic4s.requests.analyzers.LowercaseTokenFilter



object DataMapping extends ElasticDsl {
  val urlStrip: CharFilter = PatternReplaceCharFilter(
    "url_strip",
    "https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)",
    ""
  )
  val codeStrip: CharFilter = PatternReplaceCharFilter(
    "code_strip",
    "<code>[\\w\\W]*?<\\/code>",
    ""
  )
  val englishStop: TokenFilter = StopTokenFilter(
    "english_stop",
    language = Some(NamedStopTokenFilter.English)
  )
  val englishStemmer: TokenFilter = StemmerTokenFilter("english_stemmer", "english")
  val englishPossessiveStemmer: TokenFilter = StemmerTokenFilter(
    "english_possessive_stemmer",
    "possessive_english"
  )

  val englishReadme: CustomAnalyzer = 
    CustomAnalyzer(
      "english_readme",
      "standard",
      List("code_strip", "html_strip", "url_strip"),
      List("lowercase", "english_possessive_stemmer", "english_stop", "english_stemmer")
    )

  val lowercase: Normalizer =
    CustomNormalizer("lowercase", List(), List("lowercase"))

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
    dateField("updated"),
    keywordField("targetType").normalizer("lowercase"),
    keywordField("scalaVersion"),
    keywordField("scalaJsVersion"),
    keywordField("scalaNativeVersion"),
    keywordField("sbtVersion")
  )

  val referenceFields: Seq[FieldDefinition] = Seq(
    keywordField("organization").normalizer("lowercase"),
    keywordField("repository").normalizer("lowercase"),
    keywordField("artifact").normalizer("lowercase")
  )

  val releasesFields: Seq[FieldDefinition] = Seq(
    objectField("reference").fields(referenceFields),
    nestedField("maven").fields(
      keywordField("groupId").normalizer("lowercase"),
      keywordField("artifactId").normalizer("lowercase"),
      keywordField("version")
    ),
    keywordField("version"),
    keywordField("targetType").normalizer("lowercase"),
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
    objectField("dependent").fields(referenceFields),
    keywordField("dependentUrl"),
    objectField("target").fields(referenceFields),
    keywordField("targetUrl"),
    keywordField("scope")
  )
}
