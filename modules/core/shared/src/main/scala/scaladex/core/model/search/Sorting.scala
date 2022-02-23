package scaladex.core.model.search

sealed trait Sorting {
  def title: String
  def label: String
}

object Sorting {
  implicit val ordering: Ordering[Sorting] = Ordering.by {
    case Relevance    => 1
    case Stars        => 2
    case Created      => 3
    case Forks        => 4
    case Contributors => 5
    case Dependent    => 6
  }

  val all: Seq[Sorting] = Seq(Stars, Forks, Contributors, Dependent, Relevance, Created).sorted
  val byLabel: Map[String, Sorting] = all.map(sorting => sorting.label -> sorting).toMap

  object Stars extends Sorting {
    val title: String = "Stars"
    val label: String = "stars"
  }

  object Forks extends Sorting {
    val title: String = "Forks"
    val label: String = "forks"
  }

  object Contributors extends Sorting {
    val title: String = "Contributors"
    val label: String = "contributors"
  }

  object Dependent extends Sorting {
    val title: String = "Dependent"
    val label: String = "dependent"
  }

  object Relevance extends Sorting {
    val title: String = "Relevance"
    val label: String = "relevance"
  }

  object Created extends Sorting {
    val title: String = "Created"
    val label: String = "created"
  }
}
