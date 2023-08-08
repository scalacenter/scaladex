package scaladex.core.model

final case class TopicCount(topic: String, count: Int)

object TopicCount {
  implicit val ordering: Ordering[TopicCount] = Ordering.by(_.topic)
}
