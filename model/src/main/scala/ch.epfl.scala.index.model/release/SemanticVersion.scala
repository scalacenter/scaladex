package ch.epfl.scala.index.model.release

/**
 * Semantic version, separation of possible combinations
 * @param major the major version number
 * @param minor the minor version number
 * @param patch the path version number
 * @param preRelease the pre release name
 * @param metadata the release metadata
 */
case class  SemanticVersion(
  major: Long,
  minor: Long = 0,
  patch: Option[Long] = None,
  preRelease: Option[PreRelease] = None,
  metadata: Option[String] = None
) {

  /**
   * display correct and nice version as string eg: 1.0.0 | 1.2.1 | 1.2 | 1.2.0-RC1
   * @return
   */
  override def toString = {

    val patchPart = patch.map("." + _).getOrElse("")

    val preReleasePart = preRelease.map{

      case Milestone(d) => "M" + d.toString
      case ReleaseCandidate(d) => "RC" + d.toString
      case OtherPreRelease(v) => v.toString
    }.map("-" + _).getOrElse("")

    val metadataPart = metadata.map("+" + _).getOrElse("")
    
    major + "." + minor + patchPart + preReleasePart + metadataPart
  }
}

object SemanticVersion {

  /**
   * special ordering for versions
   * @return
   */
  implicit def ordering = new Ordering[SemanticVersion] {
    val LT = -1
    val GT =  1
    val EQ =  0
    
    val lcmp = implicitly[Ordering[Long]]
    val scmp = implicitly[Ordering[String]]
    val cmp = implicitly[Ordering[(Long, Long, Option[Long])]]

    def compare(v1: SemanticVersion, v2: SemanticVersion): Int = {     
      def tupled(v: SemanticVersion) = {
        import v._
        (major, minor, patch)
      }
      
      val tv1 = tupled(v1)
      val tv2 = tupled(v2)
      
      def preCmp(pr1: Option[PreRelease], pr2: Option[PreRelease]): Int = {
        (pr1, pr2) match {
          case (None, None)                                               => EQ
          case (None, Some(_))                                            => GT
          case (Some(_), None)                                            => LT
          case (Some(ReleaseCandidate(rc1)), Some(ReleaseCandidate(rc2))) => lcmp.compare(rc1, rc2)
          case (Some(ReleaseCandidate(_))  , Some(Milestone(_)))          => GT
          case (Some(Milestone(_))         , Some(ReleaseCandidate(_)))   => LT
          case (Some(Milestone(m1))        , Some(Milestone(m2)))         => lcmp.compare(m1, m2)
          case (Some(OtherPreRelease(pr1)) , Some(OtherPreRelease(pr2)))  => scmp.compare(pr1, pr2)
          case (Some(OtherPreRelease(_))   , Some(Milestone(_)))          => LT
          case (Some(OtherPreRelease(_))   , Some(ReleaseCandidate(_)))   => LT
          case (Some(_)                    , Some(OtherPreRelease(_)))    => GT
          // todo: fix that wildcard - there is a compiler error
          /*
           * It would fail on the following inputs:
           * (Some(??), Some(_))
           * (Some(Milestone(_)), Some(_))
           * (Some(OtherPreRelease(_)), Some(_))
           * (Some(ReleaseCandidate(_)), Some(_))
           * (Some(_), Some(??))
           * (Some(_), Some(Milestone(_)))
           * (Some(_), Some(ReleaseCandidate(_)))
           * (Some(_), Some(_))
           */
          case _ => EQ
        }
      }
      
      // Milestone < Release Candidate < Released
      if(cmp.equiv(tv1, tv2)) preCmp(v1.preRelease, v2.preRelease) 
      else cmp.compare(tv1, tv2)      
    }
  }
}