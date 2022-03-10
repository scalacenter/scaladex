package scaladex.view

object Formats {
  // TODO: add tests
  def plural(n: Long, word: String, plural: String = ""): String =
    n match {
      case 0                                         => s"no $word"
      case 1                                         => s"$n $word"
      case _ if plural.isEmpty && word.endsWith("y") => s"$n ${word.dropRight(1)}ies"
      case _ if plural.isEmpty                       => s"$n ${word}s"
      case _                                         => s"$n $plural"
    }

  def wordPlural(n: Long, word: String, plural: String = ""): String =
    n match {
      case 0                                         => s"$word"
      case 1                                         => s"$word"
      case _ if plural.isEmpty && word.endsWith("y") => s"${word.dropRight(1)}ies"
      case _ if plural.isEmpty                       => s"${word}s"
      case _                                         => s"$plural"
    }
}
