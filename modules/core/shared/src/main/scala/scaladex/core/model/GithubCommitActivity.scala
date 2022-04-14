package scaladex.core.model

import java.time.Instant

/**
  * 
  *
  * @param total the total number of commits during the week
  * @param week the week date starting on Sunday
  * @param days the break down of number of commits per day starting from Sunday
  */
final case class GithubCommitActivity(total: Int, week: Instant, days: IndexedSeq[Int])
