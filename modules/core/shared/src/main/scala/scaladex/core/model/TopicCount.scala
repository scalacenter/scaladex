package scaladex.core.model

final case class TopicCount(topic: String, count: Int)

object TopicCount:
  given ordering: Ordering[TopicCount] = Ordering.by(_.topic)
