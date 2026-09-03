package scaladex.view

object Formats:
  private def pluralize(word: String, plural: String): String =
    if plural.nonEmpty then plural
    else if word.endsWith("y") then s"${word.dropRight(1)}ies"
    else s"${word}s"

  def plural(n: Long, word: String, plural: String = ""): String =
    n match
      case 0 => s"No ${pluralize(word, plural)}"
      case 1 => s"$n $word"
      case _ => s"$n ${pluralize(word, plural)}"

  def wordPlural(n: Long, word: String, plural: String = ""): String =
    n match
      case 1 => word
      case _ => pluralize(word, plural)
end Formats
