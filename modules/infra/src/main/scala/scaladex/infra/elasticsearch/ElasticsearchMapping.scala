package scaladex.infra.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.analysis._
import com.sksamuel.elastic4s.fields.ElasticField

object ElasticsearchMapping {
  val urlStrip: CharFilter = PatternReplaceCharFilter(
    "url_strip",
    "https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)",
    ""
  )
  val codeStrip: CharFilter = PatternReplaceCharFilter("code_strip", "<code>[\\w\\W]*?<\\/code>", "")

  val englishStop: TokenFilter = StopTokenFilter("english_stop", language = Some(NamedStopTokenFilter.English))
  val englishStemmer: TokenFilter = StemmerTokenFilter("english_stemmer", "english")
  val englishPossessiveStemmer: TokenFilter = StemmerTokenFilter("english_possessive_stemmer", "possessive_english")

  val englishReadme: CustomAnalyzer = CustomAnalyzer(
    "english_readme",
    "standard",
    List("code_strip", "html_strip", "url_strip"),
    List("lowercase", "english_possessive_stemmer", "english_stop", "english_stemmer")
  )
  val lowercase: Normalizer = CustomNormalizer("lowercase", List(), List("lowercase"))

  val projectFields: Seq[ElasticField] = List(
    textField("organization")
      .analyzer("standard")
      .fields(keywordField("keyword").normalizer("lowercase")),
    textField("repository")
      .analyzer("standard")
      .fields(keywordField("keyword").normalizer("lowercase")),
    keywordField("artifactNames").normalizer("lowercase"),
    keywordField("deprecatedArtifactNames").normalizer("lowercase"),
    dateField("creationDate"),
    keywordField("languages"),
    keywordField("platforms"),
    intField("dependents"),
    keywordField("category"),
    textField("githubInfo.description").analyzer("english"),
    textField("githubInfo.readme").analyzer("english_readme"),
    intField("githubInfo.forks"),
    intField("githubInfo.stars"),
    intField("githubInfo.scalaPercentage"),
    intField("githubInfo.contributorCount"),
    textField("githubInfo.topics")
      .analyzer("standard")
      .fields(keywordField("keyword").normalizer("lowercase")),
    nestedField("githubInfo.openIssues"),
    objectField("formerReferences").copy(
      properties = Seq(
        textField("organization").analyzer("standard"),
        textField("repository").analyzer("standard")
      )
    ),
    intField("githubInfo.commitsPerYear")
  )
}
